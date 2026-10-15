// The profile picture (NUD-72): uploaded under my folder, mine after set_avatar, seen by my
// connections on their next sync, fetched through a signed URL, replaced, removed, and gone with
// the account.
import { describe, expect, it } from "vitest";
import { sibelAndEren } from "./fixtures";

describe("scenarios: profile picture", () => {
  it("is mine after set_avatar and reaches my connections on their next sync", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const upload = await eren.avatarUploadUrl();
    expect(upload.path.startsWith(`avatars/${eren.uuid}/`)).toBe(true);
    expect(upload.url).toContain(upload.path);

    const set = await eren.setAvatar(upload.path);
    expect(set.data?.avatarPath).toBe(upload.path);
    expect(world.person("eren").avatarPath).toBe(upload.path);

    await sibel.phone.sync();
    const seen = sibel.phone.lastSync?.connections.find((c) => c.personId === eren.uuid);
    expect(seen?.avatarPath).toBe(upload.path);
    await eren.phone.sync();
    expect(eren.phone.lastSync?.me.avatarPath).toBe(upload.path);

    const view = await sibel.avatarViewUrl(eren.uuid);
    expect(view.data).toEqual({
      url: `https://storage.test/view/${upload.path}`,
      avatarPath: upload.path,
    });
    const own = await eren.avatarViewUrl(eren.uuid);
    expect(own.data?.avatarPath).toBe(upload.path);
  });

  it("only my connections can look at it, and only while there is one", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const can = await world.addPerson("Can");
    expect((await can.avatarViewUrl(eren.uuid)).error?.error.name).toBe("not_connected");
    expect((await sibel.avatarViewUrl(eren.uuid)).error?.error.name).toBe("not_found");
  });

  it("a replaced or removed picture deletes the file before; a path outside my folder is refused", async () => {
    const { world, sibel, eren } = await sibelAndEren();
    const first = await eren.avatarUploadUrl();
    await eren.setAvatar(first.path);
    const second = await eren.avatarUploadUrl();
    await eren.setAvatar(second.path);
    expect(world.photos.deletedFiles).toEqual([first.path]);
    expect(world.person("eren").avatarPath).toBe(second.path);

    const outside = await eren.setAvatar(`avatars/${sibel.uuid}/1.jpg`);
    expect(outside.error?.error.name).toBe("validation_failed");
    expect(world.person("eren").avatarPath).toBe(second.path);

    await eren.setAvatar(null);
    expect(world.person("eren").avatarPath).toBeNull();
    expect(world.photos.deletedFiles).toEqual([first.path, second.path]);
  });

  it("goes with the account", async () => {
    const { world, eren } = await sibelAndEren();
    const upload = await eren.avatarUploadUrl();
    await eren.setAvatar(upload.path);
    await eren.deleteAccount();
    expect(world.person("eren").avatarPath).toBeNull();
    expect(world.photos.deletedFiles).toEqual([upload.path]);
  });
});
