// Smoke 2 (docs/testing.md, Smoke set): Selin invites Eren from home (1e, 1f), Eren opens the invite
// link before he has an account: the invited welcome (0b) and the prefilled name (0e, ADR 0016). He
// signs up with that number, the consent screen (0f) opens by itself, İzin ver, matched (0g), home. Both are
// connected on the server and Selin's pending invite (1g) leaves her home.
// Runs after smoke 1: Selin is signed in on her emulator.
import postgres from "postgres";
import type { RunContext } from "../context";
import { devices } from "../devices";
import { waitFor } from "../proc";

const SELIN_PHONE = "+905320000001";
const EREN_PHONE = "+905320000002";

export async function invite(run: RunContext, databaseUrl: string): Promise<void> {
  const { selin, eren } = devices;
  const db = postgres(databaseUrl, { max: 1, onnotice: () => undefined });
  try {
    await run.step("invite", async (dir) => {
      await selin.wakeAndUnlock();
      await selin.maestro("selin/invite.yaml", dir, {
        NAME: "Eren",
        PHONE: EREN_PHONE.slice(3),
        RELATIONSHIP: "spouse",
      });
    });

    await run.step("invite-on-server", async () => {
      const row = await waitFor(
        "Selin's open invite for Eren's number",
        async () => {
          const rows = await db<{ status: string; relationship: string }[]>`
            select i.status, i.relationship from invites i
            join people p on p.id = i.inviter_id
            where p.phone = ${SELIN_PHONE} and i.invitee_phone = ${EREN_PHONE}`;
          return rows[0] ?? null;
        },
        30_000,
      );
      if (row.status !== "open" || row.relationship !== "spouse")
        throw new Error(`unexpected invite ${JSON.stringify(row)}`);
    });

    await run.step("eren-sign-up-from-link", async (dir) => {
      const [row] = await db<{ code: string }[]>`
        select i.code from invites i where i.invitee_phone = ${EREN_PHONE} and i.status = 'open'`;
      if (!row) throw new Error("no open invite for Eren");
      await eren.wakeAndUnlock();
      // Straight to the app: App Links are verified only for the release certificate on nuddle.app.
      await eren.shell(
        `am start -a android.intent.action.VIEW -d https://nuddle.app/i/${row.code} -n app.nuddle.debug/app.nuddle.MainActivity`,
      );
      await eren.maestro("eren/sign-up-from-link.yaml", dir, { PHONE: EREN_PHONE.slice(3) });
    });

    await run.step("consent", async (dir) => {
      await eren.maestro("eren/consent.yaml", dir);
    });

    await run.step("connected", async () => {
      const edges = await waitFor(
        "two active edges between Selin and Eren",
        async () => {
          const rows = await db<{ from: string; to: string; type: string }[]>`
            select f.phone as from, t.phone as to, e.type from edges e
            join people f on f.id = e.from_person_id
            join people t on t.id = e.to_person_id
            where e.status = 'active'`;
          return rows.length === 2 ? rows : null;
        },
        30_000,
      );
      run.log(edges.map((e) => `${e.from} -> ${e.to} ${e.type}`).join(", "));
    });

    await run.step("invite-accepted", async (dir) => {
      await selin.maestro("selin/invite-accepted.yaml", dir);
    });
  } finally {
    await db.end();
  }
}
