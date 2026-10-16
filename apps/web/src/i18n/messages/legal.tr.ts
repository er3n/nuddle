// Source language. What is said here follows docs/product.md (Identity, Photo and approval),
// docs/architecture.md (Stack) and ADR 0017, 0019; the human reviews it before it goes live.
import { CONTROLLER, PRIVACY_EMAIL } from "../contact";
import type { Legal } from "./types";

const deleted = [
  "Telefon numaran, adın ve profil fotoğrafın.",
  "Bağlantıların ve gönderdiğin davetler.",
  "Yazdığın ve sana yazılan görevler, notlar ve fotoğraflar.",
  "Dürtme geçmişin ve senin için öğrendiğimiz dürtme özeti.",
  "Yapay zekâ çağrılarının kayıtları.",
  "Telefonlarının bildirim anahtarları.",
];

export const legalTr: Legal = {
  home: "Ana sayfa",
  otherLanguage: "English",
  privacy: {
    metaTitle: "Gizlilik · dürt",
    title: "Gizlilik metni",
    updated: "Son güncelleme: 16 Ekim 2026",
    intro:
      "dürt, birinin bir kere yazdığı işi, unutan kişiye iş bitene kadar hatırlatan bir uygulama. Bu metin, 6698 sayılı Kişisel Verilerin Korunması Kanunu (KVKK) kapsamında hangi verilerini neden işlediğimizi, kimlerle paylaştığımızı, ne kadar sakladığımızı ve haklarını anlatır.",
    sections: [
      {
        heading: "Veri sorumlusu",
        paragraphs: [
          `Veri sorumlusu ${CONTROLLER}. Gizlilikle ilgili her soru ve başvuru için: ${PRIVACY_EMAIL}`,
        ],
      },
      {
        heading: "Hangi verileri işliyoruz",
        paragraphs: [],
        items: [
          "Telefon numaran. Hesabının tek kimliği bu; giriş kodu bu numaraya SMS ile gider.",
          "Adın ve, eklersen, profil fotoğrafın.",
          "Bağlantıların: kimi davet ettiğin, aranızdaki ilişki (eşin, annen gibi) ve davet ettiğin kişinin adı ve telefon numarası. Rehberine erişmeyiz, sadece seçtiğin kişinin numarasını alırız. Davet ettiğin kişi henüz uygulamayı kullanmıyorsa numarası, o numarayla kayıt olduğunda daveti görebilmesi için saklanır.",
          "Görevler: yazdığın metin, görevin başlığı, detayı, zamanı, kime yazıldığı, notlar ve nasıl bittiği. Sesle yazdığında konuşman telefonunun konuşma tanıma hizmetiyle yazıya çevrilir; sesin bize gelmez.",
          "Dürtme geçmişin: hangi dürtmenin ne zaman çaldığı, hangisinden sonra harekete geçtiğin, ertelemeler. Bundan, seni nasıl dürtmemiz gerektiğini anlatan kısa bir özet çıkarılır.",
          "Fotoğraflar: bir işi bitirdiğini göstermek için gönderdiğin fotoğraf.",
          "Ayarların: sessiz saatler, saat dilimi ve tanıdıklarına haber verilip verilemeyeceği.",
          "Cihaz bilgileri: bildirim gönderebilmek için telefonun bildirim anahtarı, izinlerin açık olup olmadığı, dürtmelerin telefonda zamanında çalıp çalmadığı ve uygulama çökerse hata raporu (telefon modeli, Android sürümü, hata kaydı).",
        ],
      },
      {
        heading: "Neden işliyoruz",
        paragraphs: [
          "Uygulamayı sana sunabilmek için: giriş, görevleri iletmek, dürtmeleri zamanında çaldırmak, fotoğrafı karşı tarafa göstermek. Bu, aramızdaki sözleşmenin ifası için gerekli (KVKK md. 5/2-c).",
          "Uygulamanın çalışmasını korumak ve geliştirmek için: hata raporları, kötüye kullanıma karşı giriş denemelerini saymak ve yapay zekânın planlarının kalitesini ölçmek. Bu, meşru menfaatimize dayanır (KVKK md. 5/2-f).",
          "Verilerini reklam için kullanmayız, satmayız, kimseye pazarlama için vermeyiz.",
        ],
      },
      {
        heading: "Yapay zekâ",
        paragraphs: [
          "Yazdığın metni bir göreve çevirmek ve dürtmelerin ne zaman, ne kadar güçlü çalacağını planlamak için bir yapay zekâ modeli kullanırız. Modele yazdığın metin gider. Plan için ayrıca görevin kendisi, saat dilimin, sessiz saatlerin, sana yazan kişi ve bağlantılarının adları ve aranızdaki ilişki, son görevlerinin başlıkları ve nasıl bittikleri gider. Fotoğrafların gitmez.",
          "Bu çağrıların girdisini ve cevabını, planların doğru olup olmadığını görmek ve geliştirmek için saklarız. Hesabını silince bu kayıtlar da silinir.",
        ],
      },
      {
        heading: "Kimlerle paylaşıyoruz",
        paragraphs: [
          'Bağlandığın kişi: adını, profil fotoğrafını, telefon numaranı (seni arayabilmesi için) ve telefonunun şu an nasıl dürtülebildiğini görür. Birbirinize yazdığınız görevleri, notları ve fotoğrafları sadece ikiniz görürsünüz. Bir istisna: bir işi kaçırırsan, kendi bağlantılarından birine işin başlığı ve cevap vermediğin söylenebilir. Bunu Profil\'deki "Benim işlerimde tanıdıklarımı kullanabilirsin" ayarıyla kapatabilirsin.',
          "Hizmet aldığımız şirketler, sadece işlerini yapmak için gereken kadarını:",
        ],
        items: [
          "Supabase: veritabanı, giriş ve fotoğraf depolama. Sunucular Frankfurt, Almanya'da.",
          "Vercel: web sitesi ve sunucu kodu. Kod Frankfurt, Almanya'da çalışır.",
          "Google Firebase: bildirimler ve hata raporları. ABD ve diğer ülkeler.",
          "Twilio: giriş kodu SMS'i. ABD.",
          "OpenRouter ve onun üzerinden çalışan yapay zekâ modelinin sağlayıcısı. ABD ve diğer ülkeler.",
        ],
      },
      // TODO(open-questions#50): which article 9 safeguard applies.
      {
        heading: "Yurt dışına aktarım",
        paragraphs: [
          "Bu şirketler yurt dışındadır ya da yurt dışında kurulmuştur. Bu yüzden kişisel verilerin Türkiye dışına aktarılır: Almanya'ya, ABD'ye ve bu sağlayıcıların çalıştığı diğer ülkelere. Aktarım KVKK'nın 9. maddesine uygun olarak yapılır.",
        ],
      },
      {
        heading: "Ne kadar saklıyoruz",
        paragraphs: [],
        items: [
          "Fotoğraflar görev kapanınca silinir.",
          "Diğer veriler hesabın açık kaldığı sürece saklanır.",
          "Hesabını sildiğinde verilerin hemen silinir; neler silindiğini aşağıda anlatıyoruz.",
          "Hata raporları, telefon numaranla eşleşmeden, Firebase'de en fazla 90 gün kalır.",
        ],
      },
      {
        heading: "Hesabını silme",
        paragraphs: [
          "Hesabını uygulamada Profil > Hesabı sil ile silebilirsin. Uygulamaya erişemiyorsan nuddle.app/tr/delete-account sayfasındaki yolu izle. Hesabını sildiğinde şunlar silinir:",
        ],
        items: deleted,
      },
      {
        heading: "Hakların",
        paragraphs: ["KVKK'nın 11. maddesine göre şunları isteyebilirsin:"],
        items: [
          "Verilerinin işlenip işlenmediğini öğrenmek, işlendiyse bilgi istemek.",
          "Neden işlendiğini ve buna uygun kullanılıp kullanılmadığını öğrenmek.",
          "Yurt içinde ve yurt dışında kimlere aktarıldığını öğrenmek.",
          "Eksik ya da yanlışsa düzeltilmesini, silinmesini ya da yok edilmesini ve bunun aktarıldığı kişilere bildirilmesini istemek.",
          "Sadece otomatik sistemlerle analiz edilmesi sonucunda aleyhine bir sonuç çıkmasına itiraz etmek.",
          "Kanuna aykırı işleme yüzünden zarara uğrarsan zararın giderilmesini istemek.",
        ],
      },
      {
        heading: "Başvuru",
        paragraphs: [
          `Başvurunu, hesabındaki telefon numarasıyla birlikte ${PRIVACY_EMAIL} adresine yaz. En geç 30 gün içinde cevap veririz.`,
        ],
      },
      // TODO(open-questions#52): minimum age.
      {
        heading: "Çocuklar",
        paragraphs: [
          "dürt 13 yaşından küçükler için değildir. 18 yaşından küçükler dürt'ü ebeveyninin ya da vasisinin izniyle kullanmalıdır.",
        ],
      },
      {
        heading: "Web sitesi",
        paragraphs: [
          "nuddle.app sadece seçtiğin dili hatırlamak için bir çerez kullanır. Reklam ya da takip çerezi yoktur.",
        ],
      },
      {
        heading: "Değişiklikler",
        paragraphs: [
          "Bu metni değiştirirsek güncel hali bu sayfada yayımlanır ve yukarıdaki tarih güncellenir.",
        ],
      },
    ],
  },
  deleteAccount: {
    metaTitle: "Hesabını sil · dürt",
    title: "Hesabını sil",
    updated: "Son güncelleme: 16 Ekim 2026",
    intro: "dürt hesabını ve verilerini istediğin zaman silebilirsin.",
    sections: [
      {
        heading: "Uygulamadan",
        paragraphs: [
          "Uygulamada Profil'i aç, en alttaki Hesabı sil'e dokun ve Hesabımı sil ile onayla. Hesabın o an silinir.",
        ],
      },
      {
        heading: "Uygulama olmadan",
        paragraphs: [
          `Uygulamaya erişemiyorsan ${PRIVACY_EMAIL} adresine "Hesabımı silin" diye yaz ve hesabındaki telefon numarasını ekle. Numaranın sana ait olduğunu doğruladıktan sonra hesabını en geç 30 gün içinde sileriz ve sana haber veririz.`,
        ],
      },
      {
        heading: "Neler silinir",
        paragraphs: [],
        items: deleted,
      },
      {
        heading: "Karşı taraf",
        paragraphs: [
          "Sana yazılan ve senin yazdığın, henüz bitmemiş işler iptal olur ve karşı tarafa haber verilir. Bağlantıların seni artık listesinde görmez.",
        ],
      },
      {
        heading: "Ne kalır",
        paragraphs: [
          "Hata raporları, telefon numaranla eşleşmeden, Firebase'de en fazla 90 gün kalır ve sonra silinir. Başka hiçbir veri saklanmaz.",
        ],
      },
      {
        heading: "Geri alınamaz",
        paragraphs: [
          "Silinen hesap geri getirilemez. Aynı numarayla yeniden kayıt olursan boş bir hesapla başlarsın.",
        ],
      },
    ],
  },
};
