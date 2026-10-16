// Source language. Copy from docs/ux/source/Landing.dc.html; words follow docs/glossary.md.
import { legalTr } from "./legal.tr";
import type { Messages } from "./types";

export const tr: Messages = {
  brand: "dürt",
  domain: "nuddle.app",
  meta: {
    title: "dürt · Hatırlatıcı ve alarm: evde biri hep unutuyor",
    description:
      "Sen bir kere yaz. dürt, o iş bitene kadar dürter. Kaç dürtmede öğrendiğini de hatırlar.",
  },
  nav: {
    label: "Ana menü",
    how: "Nasıl çalışır",
    video: "İzle",
    levels: "Dürtme seviyeleri",
    pricing: "Fiyat",
    download: "Android'e indir",
    otherLanguage: "English",
  },
  hero: {
    pokedYou: (count) => `Bu sayfa seni ${String(count)} kez dürttü. Şimdilik.`,
    title: "Evde biri hep unutuyor.",
    titleAccent: "Artık unutamayacak.",
    lead: "Sen bir kere yaz. dürt, o iş bitene kadar dürter. Kaç dürtmede öğrendiğini de hatırlar, bir dahakine ona göre davranır.",
    primary: "Unutkanı kur",
    secondary: "Nasıl oluyor bu?",
  },
  phone: {
    label: "4. dürtme · tam ekran",
    task: "EKMEK.",
    sub: "Bu senin son erteleme hakkın.",
    button: "Aldım, işte fotoğrafı",
    excuse: "Mesajı görmedim.",
    reply: "Tam ekranı da mı?",
  },
  marquee: [
    "EKMEK AL",
    "KİRAYI YATIR",
    "FATURAYI ÖDE",
    "HER SALI ÇÖP",
    "ANNENİ ARA",
    "İLACINI İÇ",
    "KURU TEMİZLEME",
  ],
  story: {
    title: "Bir ekmeğin hikayesi",
    subtitle: "Gerçek olaylardan esinlenilmiştir. Maalesef.",
    panels: [
      {
        time: "14:32",
        caption: 'Görev düştü. Cevap: "tamam tamam."',
        alt: "Kanepede oyun oynayan biri. Telefon Ekmek al diyor, o tamam tamam diye düşünüyor.",
      },
      {
        time: "18:15",
        caption: 'Sesli dürtme. Cevap: "birazdan."',
        alt: "Telefon sesli dürtüyor, titriyor. Kanepedeki kişi birazdan diye düşünüyor.",
      },
      {
        time: "18:45",
        caption: "Tam ekran. Cevap: yok, sadece çığlık.",
        alt: "Telefon devleşmiş, ekranında kocaman EKMEK yazıyor. Kişinin gözleri fırlamış, oyun kolu havaya uçmuş.",
      },
      {
        time: "19:02",
        caption: "Ekmek eve geldi. Fotoğrafı da var.",
        alt: "Kişi kapıda, kocaman bir ekmekle gururla poz veriyor, telefonla kanıt fotoğrafı çekiliyor. Dışarıdan Onaylandı yazısı.",
      },
    ],
    drawn: {
      order: "Ekmek al",
      firstReply: "tamam tamam",
      loudOrder: "EKMEK AL!",
      secondReply: "birazdan...",
      fullScreen: "EKMEK.",
      approved: "Onaylandı",
    },
  },
  video: {
    title: "28 saniyede dürt",
    subtitle: "Kira bir kere yazılır. Gerisini izle.",
    play: "Tanıtım videosunu oynat",
    watch: "İzle · 0:28",
    posterAlt: "Videodan bir kare: 3. dürtme, tam ekran, KİRAYI YATIR.",
    note: "Oynatınca video YouTube'dan yüklenir.",
  },
  features: {
    title: "Bir de",
    titleAccent: "bunlar var.",
    items: [
      {
        tag: "Her gün 09:00",
        title: "Her gün yazma",
        body: '"Annem her gün 9\'da ilacını içsin." Bir kere yazılır, her gün dürter. "Her salı" ve "hafta içi her sabah" da olur. "Bugün olmaz" o günü atlar, iptal edince biter.',
      },
      {
        tag: "Mesaj",
        title: "Söylemeyi unutma",
        body: '"Sibel\'e 3\'te seni seviyorum de." Yapılacak bir iş yok: saati gelince bir kere çalar, "Tamam" deyince kapanır. Israr etmez.',
      },
      {
        tag: "Dinliyorum",
        title: "Yazmaya üşenene",
        body: "Mikrofona dokun, söyle. Söylediğin kutuya yazılır, istersen düzeltip gönderirsin.",
      },
      {
        tag: "Saat 17:00 olunca",
        title: "Şimdi değil, sonra",
        body: "Dürtülen, dürtmelerin başlayacağı saati kaydırabilir. Son zaman yerinde kalır, bahane kalmaz.",
      },
      {
        tag: "Ertele · 22:37",
        title: "Ertelemenin de hesabı var",
        body: "Hakkı sınırlı, kalan süreye göre kısalır. Düğme ne zaman tekrar çalacağını söyler, son hakkı olduğunu da.",
      },
      {
        tag: "✓✓ Gördü",
        title: "Yazan da bilir",
        body: 'Ulaştı mı, gördü mü, kaç kez dürtüldü: "3 kez dürttüm, en son 18:15." Plan gizli kalır, sadece olan söylenir.',
      },
      {
        tag: "Profil fotoğrafı",
        title: "Kimden geldiği belli",
        body: "Fotoğrafını galeriden seç. Dürtme bildiriminde yazanın yüzü görünür, annenin dürtmesini uzaktan tanırsın.",
      },
      {
        tag: "Kendine",
        title: "Kendine de yaz",
        body: "Bizimkiler'de kimse yoksa da olur. Kendine yazdığın işi de aynı ısrarla dürter.",
      },
    ],
  },
  medicine: {
    title: "İlaç hatırlatıcı:",
    titleAccent: "annen unutmasın.",
    lead: '"Annem her gün 9\'da tansiyon ilacını içsin." Bir kere yaz, dürt her sabah onu dürtsün. İçip içmediğini de sen bil.',
    items: [
      {
        tag: "Her gün 09:00",
        title: "Bir kere yazılır",
        body: "Her gün aynı saatte çalar. Buzdolabına not, telefonda alarm kurmak yok. Bitince iptal edersin.",
      },
      {
        tag: "Tam ekran",
        title: "Görmezden gelinmez",
        body: 'Bildirimi kaçırırsa ses yükselir, sonra tam ekran alarm gelir. "Bitti" diyene kadar dürter.',
      },
      {
        tag: "✓ Bitti",
        title: "Sen de bilirsin",
        body: "İçince sana haber gelir. İçmediyse onu da görürsün, arayıp sorarsın.",
      },
    ],
    note: "dürt bir hatırlatma uygulamasıdır, tıbbi tavsiye vermez. İlacın nasıl kullanılacağını doktoruna sor.",
  },
  steps: {
    title: "Üç adım.",
    titleSecondLine: "Sıfır bahane.",
    items: [
      {
        title: "Bir kere yaz ya da söyle",
        body: '"Eren yarın 9\'a kadar kirayı yatırsın." Kime, ne, ne zamana kadar: gerisini yapay zeka çıkarır. "3\'te" bir an, "9\'a kadar" bir son zamandır. Form yok, tarih seçici yok, sabır testi yok.',
      },
      {
        title: "O dürtülür",
        body: "Doğru saatte, giderek artan şiddetle. Bir akşam önceden rahatsız etmez, son dakikaya da bırakmaz. Hangi dürtmede uyandığını öğrenir.",
      },
      {
        title: '"Bitti" de, gerekirse fotoğrafla',
        body: 'Çoğu iş için "Bitti" yeter. Para gibi işlerde fotoğraf önerilir: uygulamada çekilir ya da galeriden seçilir, onayı işi yazan verir. "Aldım ya" devri kapandı.',
      },
    ],
  },
  levels: {
    title: "Dürtme",
    titleAccent: "seviyeleri",
    lead: "Herkes aynı dürtmeyle uyanmaz. Kimisi fısıltıya döner, kimisi ancak telefonu ekmeğe dönüşünce. Dene bakalım, sen kaçıncıda uyanıyorsun.",
    button: ["Beni dürt", "Bir daha", "Hâlâ duymadım", "Tamam tamam", "Pes ediyorum", "Baştan al"],
    items: [
      { title: "Fısıltı", body: "Ekmek? Hani şey... ekmek." },
      { title: "Hatırlatma", body: "Sesli bildirim. Kibar ama kararlı." },
      { title: "Israr", body: "EKMEK. Titreşimli." },
      { title: "Tam ekran", body: "Telefonun artık bir ekmek." },
      { title: "Eşine haber", body: "Söylüyorum ha. Söylüyorum bak." },
    ],
  },
  excuses: {
    title: "Bahane imha bölümü",
    items: [
      { excuse: "Mesajı görmedim.", reply: "Tam ekran alarmı da mı görmedin?" },
      { excuse: "Unutmuşum.", reply: "Dört kere hatırlattık. Kayıtlı." },
      { excuse: "Aldım ya, arabada kaldı.", reply: "Fotoğrafını alayım o zaman." },
      { excuse: "Bugün dememiştin ki.", reply: "14:32, kelimesi kelimesine. İster misin?" },
    ],
  },
  pricing: {
    title: "Bir ekmekten ucuz.",
    titleAccent: "Neredeyse.",
    free: {
      label: "Ücretsiz",
      price: "0 TL",
      features: [
        "Uygulama içinden serbest yazma, istersen sesle",
        "Tekrarlayan görevler ve tek seferlik mesajlar",
        "Adaptif dürtme ve tam ekran alarm",
        "İstersen fotoğraf, onayı yazan verir",
        "Bizimkiler: sınırsız unutkan",
      ],
      cta: "Başla",
    },
    premium: {
      label: "Premium",
      tag: "Unutkanlar için",
      // TODO(open-questions#10): the price is not decided; the page says "soon" until it is.
      price: "Yakında",
      features: [
        "E-posta ile görev: faturayı ilet, son ödeme tarihi çıksın",
        "SMS ile görev, uygulamayı açmadan",
        "En inatçılar için sesli arama",
        "Aylık dürtme karnesi (açarsan)",
      ],
    },
  },
  faq: {
    title: "Sıkça sorulan, sonra unutulan sorular",
    items: [
      {
        question: "Bunu bana eşim kurdu. Kaldırabilir miyim?",
        answer: "Kaldırabilirsin. Ama ekmeği yine de alman gerekecek.",
      },
      {
        question: "Gece de dürter mi?",
        answer:
          "Hayır. 23:00 ile 07:00 arası sessiz. Biz de insanız, yani yazılımız ama anlıyorsun.",
      },
      {
        question: "Bir akşam önceden mi başlar?",
        answer:
          "Yarın sabah yapılacak iş için seni bu akşam rahatsız etmez. Doğru saati kendisi hesaplar.",
      },
      {
        question: "Her gün aynı şeyi yazmam mı gerekecek?",
        answer:
          '"Her gün 9\'da", "her salı" ya da "hafta içi her sabah" yaz, kendisi tekrarlar. Günde birden sık olmaz, o kadar da değil.',
      },
      {
        question: "Şu an müsait değilim, sonra dürtse?",
        answer:
          "Dürtmelerin başlayacağı saati kaydırabilirsin. Son zaman yerinde kalır, ekmek yine alınır.",
      },
      {
        question: "Galeriden eski fotoğraf yüklesem?",
        answer: "Yükleyebilirsin. Ama onayı işi yazan verir, o da dünkü ekmeği tanır.",
      },
      {
        question: "iPhone var mı?",
        answer: "Şimdilik sadece Android. iPhone'u da yapacağız, bize de hatırlatan biri lazım.",
      },
    ],
  },
  download: {
    title: "Bir dahaki ekmek",
    titleSecondLine: "garanti.",
    store: "Google Play'den indir",
    storeSoon: "Yakında Google Play'de",
    iphone: "iPhone sürümü de gelecek. Unutmazsak.",
  },
  footer: {
    quietHours: "23:00 - 07:00 arası kimse dürtülmez.",
    privacy: "Gizlilik",
    deleteAccount: "Hesabı sil",
  },
  invite: {
    title: "Seni dürt'e davet ettiler.",
    body: "Uygulamayı yükleyip davet edilen numarayla giriş yapınca davet seni bekliyor olacak.",
    more: "dürt nedir?",
  },
  notFound: { title: "Bu sayfa yok. Biz de unutmuş olabiliriz.", home: "Ana sayfaya dön" },
  legal: legalTr,
};
