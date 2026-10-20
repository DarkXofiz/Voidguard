# VOIDGUARD AntiCheat

Paper / Purpur sunucuları için **sunucu tarafında**, **kanıta dayalı** bir AntiCheat eklentisi.
Oyuncunun bilgisayarındaki dosyaları taramaz, istemciye erişmez; yalnızca sunucunun doğrulayabildiği
hareket, saldırı, blok ve olay davranışlarını inceler.

> **Önemli:** Hiçbir kontrol %100 hile tespiti garantisi vermez. Varsayılan ayarlar temkinlidir ve haksız
> ban riskini azaltmaya odaklanır. Canlı sunucuda önce `punishment.dry-run: true` ile gözlemlemeniz önerilir.

## Uyumluluk

| Öğe | Değer |
|---|---|
| Sunucu | Paper / Purpur / Spigot **1.13 → 1.21.x** (tek jar) |
| Derleme hedefi | `paper-api 1.13.2` (en düşük desteklenen API), `api-version: 1.13`, **Java 8 bytecode** |
| Çalışma Java'sı | Sunucunun gerektirdiği sürüm (1.13–1.16: Java 8+, 1.17: 16+, 1.18–1.20.4: 17+, 1.20.5+: 21) |
| Ek bağımlılık | **Yok** (ProtocolLib/PacketEvents kullanılmaz) |
| Veritabanı | SQLite — sürücü (`org.sqlite.JDBC`) Paper/Purpur/Spigot ile birlikte gelir |
| Desteklenmeyenler | **1.8–1.12** (BoundingBox, Particle API ve Material düzleştirmesi yok → ayrı bir legacy katman gerekir), Folia |

Sürümler arası farklar `util/Compat` içinde çözülür:

* **TPS, ping, `World#getMinHeight`, `Bukkit#getOfflinePlayerIfCached`** yansıma ile çağrılır; yoksa güvenli varsayılan kullanılır
  (TPS=20, ping=0 → ping toleransı uygulanmaz, lag kapısı yalnızca ölçülen gecikmeye dayanır).
* **`Material#isAir`, `Tag.CLIMBABLE`** yerine isim tabanlı kontrol kullanılır.
* **Etki adları** (`JUMP`/`JUMP_BOOST`, `FAST_DIGGING`/`HASTE`, `DAMAGE_RESISTANCE`/`RESISTANCE`), **parçacık adları**
  (`REDSTONE`/`DUST`, `EXPLOSION_LARGE`/`EXPLOSION` …) ve **ses adları** (metin anahtarı) çalışma zamanında çözülür.
* Yeni sürümlerde bir API değişirse ilgili özellik sessizce devre dışı kalır (ör. parçacık türü çözülemezse görsel efekt
  kapanır, ceza yine uygulanır); sunucu veya eklenti çökmez.

> **Dürüst not:** Bu uyumluluk, API'lerin belgelenmiş sürüm geçmişine göre tasarlandı; ben bu ortamda her sürümde
> çalıştıramadım. Üretime almadan önce kullandığınız sürümde test sunucusunda `punishment.dry-run: true` ile deneyin.
> Eski sürümlerde (1.13–1.15) bazı fizik değerleri (ör. sprint-zıplama, buz sürtünmesi) farklı olabilir; gerekirse
> `checks.speed.base-speed/tolerance` değerlerini gevşetin.

## Kurulum ve derleme

```bash
mvn clean package
# çıktı: target/VoidGuard-1.0.0.jar  -> sunucunun plugins/ klasörüne kopyalayın
```
İlk açılışta `plugins/VoidGuard/config.yml` ve `voidguard.db` oluşur. Değişikliklerden sonra `/vg reload`.

## Komutlar ve izinler

| Komut | İzin | Açıklama |
|---|---|---|
| `/vg help` | `voidguard.command` | Yardım |
| `/vg check <oyuncu>` | `voidguard.check` | Anlık VL, ping, muafiyet durumu |
| `/vg alerts` | `voidguard.alerts` | Uyarıları aç/kapat |
| `/vg info <oyuncu>` | `voidguard.info` | UUID, durum, aktif ban, ban geçmişi, ihlal sayısı |
| `/vg violations <oyuncu>` | `voidguard.violations` | Son kayıtlı ihlaller ve kanıtlar |
| `/vg unban <oyuncu>` | `voidguard.unban` | Ban kaldır |
| `/vg reload` | `voidguard.reload` | Yapılandırmayı yenile |
| `/vg test` | `voidguard.test` | Tanılama + efekt **önizlemesi** (yalnızca komutu çalıştıran kişide, ceza yok) |

`voidguard.admin` hepsini içerir. `voidguard.bypass` (varsayılan: kimse) tüm kontrollerden muaf eder.
Varsayılan olarak OP'ler de muaftır (`exempt.ops`); test oyuncuları `exempt.players` listesine yazılır.

## Mimari

```
com.voidguard.anticheat
 ├─ VoidGuard                 ana sınıf, bağlama
 ├─ checks/                   13 kontrol (her biri ayrı sınıf) + Check tabanı + CheckManager
 ├─ punishments/              ViolationTracker (VL+azalma), ActionDecider (karar), ViolationManager,
 │                            PunishmentManager (ban/kick), BanRecord
 ├─ storage/Database          SQLite, tek arka plan iş parçacığı (ana iş parçacığı bloke edilmez)
 ├─ effects/PunishmentEffect  kırmızı-mor halka + TNT benzeri parçacık/ses animasyonu
 ├─ commands/VgCommand        /vg komutu
 ├─ listeners/                Bukkit olay dinleyicileri
 ├─ data/                     PlayerData, MoveCtx (olay başına tembel hesaplanan bağlam)
 ├─ math/                     Bukkit'siz saf matematik (test edilebilir)
 └─ util/                     dünya sorguları, muafiyetler, performans kapısı
```

## Karar ve ceza sistemi

Her oyuncu için her kontrolün ayrı **VL**'si tutulur ve zamanla azalır (`violations.decay-per-minute`).

1. **Düşük şüphe** (VL < `alert-vl`): yalnızca kayıt (SQLite `violations` tablosu).
2. **Orta** (VL ≥ `alert-vl`): `voidguard.alerts` iznine sahip yetkililere ve konsola uyarı.
3. **Yüksek**: ceza yalnızca şu durumlarda uygulanır:
   * **En az `min-distinct-checks` (varsayılan 2) FARKLI kontrol** `punish-vl` eşiğini aşarsa → *yüksek güven*; veya
   * Tek bir kontrol `punish-vl × single-check-multiplier` (varsayılan 40×4=160) değerini aşarsa → ceza var, **görsel efekt yok**.
4. Ban öncesinde tetiklenen kontroller, VL'ler ve son kanıtlar `bans` tablosuna kaydedilir (UUID, tarih, kontrol, kanıt özeti).
5. Ceza türü: `KICK`, `TEMPBAN` (varsayılan, `1d`) veya `BAN`. `/vg unban` ile kaldırılır; aktif banlar yeniden başlatmada SQLite'tan geri yüklenir.
6. Ceza animasyonu sırasında oyuncu çıkarsa ceza **yine uygulanır** (ban kaçışı engellenir).

Yanlış pozitifleri azaltmak için: ardışık ihlal tamponu (`buffer`), TPS/lag/ping kapısı, ışınlanma/geri tepme/oyun modu/uçuş
izni değişimi sonrası tolerans, elytra/riptide/araç muafiyeti, OP/izin/isim muafiyeti.

## Kontroller: ne tespit eder, sınırları

| Kontrol | Tespit | Bilinen sınırlar |
|---|---|---|
| **KillAura** | Bakış–hedef arası imkânsız açı; 300 ms içinde ≥3 farklı hedef; vuruş sayısı ≫ kol sallama sayısı | Yüksek hızlı hedefler + lag açıyı bozabilir (tamponlu). Yalnızca açı/örüntü; "sessiz" aura açıyı doğru tutarsa yakalanmayabilir |
| **Reach** | Göz→hedef çarpışma kutusu mesafesi, ping'e göre genişletilmiş sınır (varsayılan 3.1 + en çok 1.0) | Ping > `max-ping` ise atlanır; hafif reach (≈3.2) lag ile ayırt edilemez |
| **Fly** | Zıplama yüksekliği aşımı; havada sabit durma; yerçekimi tahminine aykırı dikey hız | Uçuş izni, elytra, sıvı, merdiven, ağ, araç, slime/bal/yatak, tekne, geri tepme muaf. Çok hafif "glide" kaçabilir |
| **Speed** | Tek olayda yatay mesafe > sprint-zıplama sınırı (efekt, walkSpeed, buz, lag dahil) | Özel eklenti hız mekanikleri `base-speed`/`tolerance` ayarı gerektirebilir |
| **NoFall** | İstemci "yerde" derken altında blok yok; ≥6 blok düşüp hasar almama | Hasarı kendi iptal eden eklentiler (ör. özel kitler) yanlış pozitif verebilir; kapatılabilir (`check-damage`) |
| **Spider** | Duvara temas ederken zıplama süresini aşan veya sabit hızlı yükselme | Merdiven/sarmaşık/iskele muaf; çok yavaş tırmanma tespit edilemez |
| **Jesus** | Derin sıvı yüzeyinde başı dışarıda, dikey konum sabit, yatay hareket | Nilüfer yaprağı, tekne, yüzme, sığ su muaf |
| **AutoClicker** | CPS > `max-cps`; ≥40 tıklamada yapay derecede düzenli aralık | Olaylar tick'e hizalı işlendiğinden (50 ms) yalnızca çok düzenli tıklayıcılar yakalanır; blok kırarken analiz yok |
| **Scaffold** | Saniyede aşırı blok; bakılmayan bloğa yerleştirme; ayak altına koyarken bakış yukarıda; bakış ışını uyuşmazlığı | Hızlı legit köprü oyuncuları (ninja/god bridge) için eşikleri gevşek tutuldu |
| **AntiKnockback** | Hasar sonrası (ping + 4 tick) geri tepme yönünde neredeyse hiç ilerlememe | Duvar/engel, sıvı, ağ, yeni vuruş durumlarında değerlendirilmez; girdi modellemesi yoktur |
| **FastBreak** | Kazma süresi, sertlik/alet/Efficiency/Haste'ten hesaplanan **alt sınırdan** kısa | Alt sınır en iyi alet varsayar (muhafazakâr). Özel eklenti aletleri/nitelikleri çakışabilir |
| **Inventory** | Saniyede aşırı tıklama; sunucunun bildiği arayüz açıkken yürüme; yaratıcı tıklaması (yaratıcı modda değilken) | Oyuncunun kendi envanterini açması sunucuya bildirilmez, yalnızca container/menü arayüzleri izlenir |
| **Packet** | 10 sn'de aşırı hareket olayı (Timer benzeri); tek olayda >10 blok; hotbar/atma/etkileşim/eğilme oranları | Ham paketler okunmaz (ek bağımlılık yok); yalnızca Bukkit olayları görünür |

## VOIDGUARD görsel ceza efekti

Yalnızca **yüksek güven** kararından sonra, `effect.enabled: true` iken çalışır:

* Oyuncunun çevresinde **kırmızı ve mor** parçacıklardan halka; süre boyunca genişleyip daralır ve döner.
* Oyuncu animasyon boyunca **sabitlenir** (hareket olayı ile; bakış serbest). Bitince, çıkışta veya eklenti kapanırken otomatik kalkar.
* **Yukarı kaldırma** (`effect.lift.enabled`) **varsayılan kapalı**; açılırsa üstü boş değilse kısaltılır ve bitişte oyuncu eski konumuna geri ışınlanır.
* **Sahte eşya** (`effect.fake-items.enabled`) **varsayılan kapalı**; yalnızca eşya *parçacığı* gösterir, envantere dokunmaz.
* Sonda **TNT benzeri** patlama parçacıkları ve sesi. **Gerçek patlama, blok hasarı, TNT varlığı yoktur.**
* Oyuncu çevrimdışıysa animasyon çalışmaz; ceza doğrudan uygulanır.
* Performans: parçacık sayısı/tick, süre, tick aralığı ve eşzamanlı efekt sayısı sınırlıdır; TPS düşükse efekt oynatılmaz (ceza yine uygulanır).

## Performans notları

* Ağır iş yok: SQLite işlemleri tek arka plan iş parçacığında; login ban kontrolü bellek önbelleğinden.
* Hareket başına blok sorguları tembel ve olay başına paylaşılır (`MoveCtx`); yüklü olmayan chunk'lar asla yüklenmez.
* Oyuncu çıkışında tüm geçici veriler temizlenir; saymaçlar ve kanıt listeleri üst sınırlıdır.
* Bir kontrol tekrar tekrar hata verirse kendini devre dışı bırakır ve loglar.

## Testler

`mvn test` — Bukkit gerektirmeyen birim testleri: normal sprint-zıplama, hız hilesi, SPEED efekti/buz, lag ölçeklemesi,
yüksek ping reach toleransı, zıplama yüksekliği/yerçekimi, geri tepme (yok sayma/ping gecikmesi), blok kırma alt sınırı,
elytra / izinli uçuş / ışınlanma / geri tepme / yaratıcı / araç muafiyetleri, TPS ve ping kapısı, tıklama analizi,
VL azalması, karar mantığı (uyarı/ceza/yüksek güven), süre ayrıştırma.

Bukkit'e bağlı sınıflar (kontroller, dinleyiciler, efekt) bir sunucu olmadan birim testlenemez; bunlar için
bir test sunucusunda `punishment.dry-run: true` ve `logging.debug: true` ile saha testi yapın.

## Lisans

MIT — bkz. `LICENSE`.
