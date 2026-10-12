// An invite link opened where the app is not installed (open-questions#13). The real page, with store
// links, comes with the landing page (feature slice 10). The invite does not depend on this link: it
// waits for whoever signs up with the invited number (docs/product.md, Connections).
export default function InvitePage() {
  return (
    <main>
      <h1>dürt</h1>
      <p>
        Seni dürt&apos;e davet ettiler. Uygulamayı yükleyip davet edilen numarayla giriş yapınca
        davet seni bekliyor olacak.
      </p>
    </main>
  );
}
