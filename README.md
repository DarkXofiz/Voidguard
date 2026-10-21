# VoidGuard

**Paper/Spigot 1.13 – 1.21.x** için kanıt tabanlı, düşük yanlış-pozitifli AntiCheat (Java 8+ ile çalışır). 1.13 API'sine karşı derlenir,
NMS kullanmaz; sonradan eklenen/yeniden adlandırılan API'ler (`Compat` sınıfı) yansıma ile çağrılır, bu yüzden tek jar tüm sürümlerde yüklenir.

## Derleme
Yerelde (derleme için JDK 21 önerilir, çıktı Java 8 bayt kodudur; Gradle ayrıca kurmanız gerekmez, `gradlew` 8.10'u kendisi indirir):
```
./gradlew build         # build/libs/VoidGuard-1.0.0.jar  (+ 96 çekirdek test)
./gradlew coreTests     # yalnızca tespit mantığı testleri
```
Windows'ta `gradlew.bat build`. JAR'ı sunucunun `plugins/` klasörüne atın.

### GitHub Actions
`.github/workflows/build.yml` her push/PR'da: wrapper doğrulama → JDK 21 → `./gradlew build` (testler dahil) → jar'ı
**Actions > Artifacts > VoidGuard** olarak yükler. `v1.0.0` gibi bir etiket push'larsanız jar otomatik **GitHub Release** olur:
```
git tag v1.0.0 && git push origin v1.0.0
```
Windows'tan yüklerseniz `gradlew` çalıştırma biti kaybolabilir; workflow `chmod +x gradlew` ile bunu zaten düzeltir
(yerelde için: `git update-index --chmod=+x gradlew`).

## Müdahale sistemi
**1) Anında engel (log düştüğü an):** bir kontrol kanıt kaydedince hileye özel kısa engel devreye girer (ihlal sayacından bağımsız):
- Saldırı hileleri (Aura/Reach/Aim): vuruşlar 3 sn iptal. **Duvar arkası vuruş ve bakmadan (≥70° sapma) vuruş her seferinde anında iptal edilir.**
- Hareket hileleri (Fly/Speed/Step/HighJump/Jesus/Spider/Phase/NoFall/NoSlow): son temiz konuma geri alma + 2 sn **yerinde dondurma**
  (yatay hareket ve yukarı çıkış engellenir; düşüş ve bakış serbest; sıvı/lav/araç/elytra içindekiler tuzağa düşmesin diye serbest).
- Scaffold: blok koyma, ChestStealer: sandık işlemleri iptal.

**2) Kalıcı ihlal (UUID bazlı, `data/violations.dat`):** puan `confirm-points`'i aşarsa ihlal sayılır (aynı hile için en fazla 30 sn'de bir):

| İhlal | Müdahale | Mesaj |
|---|---|---|
| 1 | 15 sn hileye özel engel | `§e§lVoidGuard §8» §eŞüpheli hareketlerin algılandı! 15 saniye boyunca saldırıların engellendi. §7(1/3)` |
| 2 | 30 sn kısıtlama | `§e§lVoidGuard §8» §6Hile şüphesi devam ediyor! Hareketlerin kısıtlandı. §7(2/3)` |
| 3 | **7 gün BAN** (ban-uygun hile + güçlü kanıt) | `§c§lVoidGuard §8» §cBirden fazla hile ihlali tespit edildi. Sunucudan yasaklandın. §7(3/3)` |

**Ban sahnesi:** ban kararı verilince oyuncu hemen atılmaz. Yerinde donar (hareket, saldırı, blok, komut kilitli),
etrafında kırmızı partikül çemberi döner, yukarı doğru havalanır ve tepede ban ekranıyla atılır. Ban sahne başlamadan
kaydedilir, sahne sırasında çıkan oyuncu yine banlıdır. Ayar: `sanctions.void-sequence.*` (kapatılırsa anında atılır).

**Log kuralları (en önemlisi):**
- Yetkililere giden "gerçek" log = bir kontrolün puanı eşiği aştı. Satır biçimi: `⚠ VOIDGUARD » oyuncu | Kontrol (puan) | LOG 2/3 | detay | ping`.
- **2. gerçek log:** oyuncunun vuruşları **30 sn** engellenir.
- **3. log (ban-uygun hile):** oyuncu birden **donar**, etraf **kararır**, **5 blok yükselir** ve **30 gün ban** yer.
- Hile açmayanlar için koruma: yalnızca ban-uygun kontroller sayılır, loglar arası en az 3 sn olmalı, sayaç 180 sn sonra sıfırlanır, lag/muafiyette kanıt üretilmez.
- Fly/Step/HighJump (dikey fizik) TPS düşüşünde kapanmaz; sürekli havada kalma ayrıca yakalanır.
- Ayarlar: `sanctions.log-ban.*`, `sanctions.void-sequence.*`, `sanctions.ban-duration-days`.

Ban eklentinin kendi UUID tabanlı listesindedir (sunucu ban listesine bağımlı değil, sürümden bağımsız): oyuncu giriş yapamaz,
kalan süre gösterilir, sunucu yeniden başlasa da sürer. Kaldırmak için `/vg unban <oyuncu>`. Süre ve açma/kapama: `sanctions.ban-*`.
AutoClicker, Criticals, ChestStealer, GuiMove, Velocity, Rotation, AimAssist **ban-uygun değildir** (en fazla geçici kısıt).

Çıkıp girme: girişte kontroller sıfırdan kurulur, ihlaller ve bitmemiş kısıtlar defterden geri yüklenir. Dünya değişimi,
respawn, teleport ve kendi geri-ışınlamamız için ayrı kısa tolerans vardır. Defter periyodik (asenkron, atomik) ve kapanışta kaydedilir.

## Aura / saldırı tespiti
Her vuruşta: (1) hedefin ping geçmişindeki **tüm** konumlarına 5 noktalı duvar ışını, (2) bakış–hedef açısı, (3) Reach, (4) bakış ışını–hitbox,
(5) AimAssist (nişanın hedef merkezine fazla kilitli/tutarlı olması), (6) çoklu hedef, keskin dönüş ve **sessiz rotasyon** (dön–vur–geri dön).
Duvar ve açı kontrolleri TPS düşüşünde kapanmaz (yalnızca muafiyet ve ping > 700 ms'de atlanır).

## OP bildirimleri
Oyuncu **atılmadığında** (ceza kapalıyken) tespitler OP'lere görünür (`alerts:` bölümü):
- Uyarı satırları kırmızıdır: `[VoidGuard] oyuncu » Kontrol (puan) detay`.
- OP olan oyuncular `voidguard.alerts` izni olmasa da her kanıtı sohbette görür (`ops-see-all-evidence`).
- Ceza eşiği aşılıp oyuncu atılmadıysa OP'lere "eşik aşıldı ama ceza KAPALI, atılmadı" mesajı gider.
- Kişisel kapatma: `/vg alerts`.

## Yanlış pozitif koruması
- **Lag**: TPS < 18, ping > 400 ms veya ani ping sıçramasında kanıt toplanmaz.
- **Muafiyetler**: creative/spectator, uçuş izni, elytra, araç, riptide, teleport/giriş/respawn/dünya/mod değişimi
  sonrası tolerans, sunucu hız verdikten (knockback, patlama, rüzgar topu) sonra tolerans.
- **Ortam**: sıvı, tırmanılabilir blok, ağ, bal/slime/yatak, piston, buz → ilgili kontroller atlanır.
- **Tampon**: tek bir şüpheli olay kanıt üretmez; her kontrol kendi `FlagBuffer`'ında birikir.
- **Çoklu bağımsız kanıt**: ceza için toplam puan **ve** en az 2 farklı kontrolde yeterli puan gerekir.
- **Eşikler ölçüldü**: Speed/Fly limitleri, bunny-hop dahil vanilla fizik simülasyonundan türetildi.
- Bedrock (Geyser) oyuncularında rotasyon/aim/clicker kontrolleri kapalı (`bedrock-prefix`).
- Plugin GUI menüleri ChestStealer kapsamı dışındadır (yalnızca gerçek blok konteynerleri).

## Hile → kontrol eşlemesi (ThunderHack + Wetsuki analizi)
| İstemci modülü | Kontrol | Yöntem |
|---|---|---|
| Aura (Track/rotasyonsuz/sessiz), TriggerBot, AimBot | Aim, Aura, AimAssist, Rotation, AutoClicker | bakış ışını–hitbox, çoklu hedef, snap, duvar, fare GCD, vuruş ritmi |
| Reach, HitBox | Reach, Aim | göz–kutu mesafesi (ping penceresindeki hedef geçmişiyle), genişletilmiş kutu ıskalama |
| Criticals | Criticals | vuruş öncesi zeminden mikro-zıplama (0.0625 vb.) |
| Velocity / AntiKnockback (Wetsuki "Cancel") | Velocity | verilen hıza karşı gerçek yer değiştirme oranı |
| Flight, HighJump, Step, Spider(dolaylı), Strafe-Elytra yok | Fly, HighJump, Step | yerçekimi modeli, havada sabitlik, yükselme sınırı |
| Speed, LongJump (dolaylı) | Speed | anlık + 20 tik ortalama limit |
| NoFall (Rubberband/sahte onGround) | NoFall | istemci onGround iddiası ↔ sunucu çarpışması, fallDistance sapması |
| Timer, TickShift, Blink (kısmi) | Timer | paket zamanlaması kredi modeli |
| NoSlow | NoSlow | eşya kullanırken hız |
| Scaffold, Tower | Scaffold | imkansız yüz, bakış–blok ışını, yerleştirme hızı |
| ChestStealer, AutoTotem (kısmi), GuiMove/InvMove | ChestStealer, GuiMove | açılış sonrası ilk tık, ritim, konteyner açıkken yürüme |
| Phase | Phase | önceki konum serbest, yeni konum katı blok içinde |
| Jesus | Jesus | sıvı yüzeyinde (içinde değil) düz yatay ilerleme |
| Spider | Spider | yukarı yönlü yerçekimi sapması + duvar yanında |

## Bataklık/çamur yanlış pozitifi (düzeltildi)
Zemin sorgusu bloğu "tam küp" sanıyordu; çamur (0.875), ruh kumu, tarla ve patika tam küp değildir, bu yüzden oyuncu
bunların üstündeyken "havada" (Fly/Hover/NoFall) veya blok "içinde" (Phase) görünüyordu. Artık her zaman gerçek çarpışma şekli kullanılır.
Bu düzeltme koddan türetildi; gerçek sunucuda bataklıkta doğrulayın.

## Bilinen sınırlar (dürüst liste)
- **Paket katmanı yok.** Yalnızca Bukkit olayları kullanılır. Sonuçları:
  alt-tik tıklama zamanlaması güvenilmez (bu yüzden tik bazlı + geniş örneklem), `AirStuck`/`Blink`/`TickShift`
  yalnızca Timer üzerinden dolaylı ve zayıf yakalanır, `onGround` bayrağına bağımlı kontroller sunucu çarpışma
  sorgusuyla desteklenir.
- **Kapsam dışı / zayıf:** FastUse, SpeedMine, NoJumpDelay,
  Invsee/Region exploit'leri (istemci dosyalarıyla çalışır, sunucudan görünmez), EnderChest "sahte kapama".
  `ClickTP` gibi >10 blokluk tek adımlar şüphe değil **tolerans** sayılır (başka plugin'in teleportunu cezalandırmamak için).
- Reach yaklaşık **≥ 3.4–3.5** hilelerini yakalar; 3.1–3.3 gibi küçük uzatmalar bilinçli olarak geçer.
- Rotation (GCD) kontrolü sinematik kamera/özel istemcilerde yanlış pozitif verebilir; bu yüzden düşük ağırlıklıdır.
- Folia desteklenmez.

## Test durumu
- Çekirdek mantık (`core/`): **106 otomatik test geçiyor** (ihlal defteri kalıcılığı/relog, ceza basamakları ve ban kilitleri, Jesus dahil); meşru yürüme/sprint/bunny-hop/düşme/knockback/lag
  senaryolarında 0 kanıt, hile senaryolarında tespit.
- Paper adaptörü (`bukkit/`): gerçek Paper API'si ile **derlenmedi ve canlı sunucuda denenmedi**
  (geliştirme ortamında API indirilemedi; yalnızca kendi stub'larımla iç tutarlılık denendi). İlk `./gradlew build`
  derleme hatası verirse çıktıyı paylaşın, düzeltilir.
- **Test EDİLMEYENLER:** geri-ışınlama, vuruş/blok/konteyner iptali, relog'da kısıt geri yükleme ve oyuncu mesajları
  Paper adaptöründe yer aldığı için yalnızca derleme düzeyinde (stub) kontrol edildi; canlıda doğrulanmalıdır.
- Gerçek hile istemcisiyle test yapılmadı; eşikler fizik simülasyonuna dayanır. Canlıda log-only modda doğrulayın.

## Komutlar
`/vg alerts` · `/vg status [oyuncu]` · `/vg exempt <oyuncu> [dk]` · `/vg reset <oyuncu>` · `/vg reload`
İzinler: `voidguard.admin`, `voidguard.alerts`, `voidguard.bypass`.
