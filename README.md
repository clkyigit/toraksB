# toraksB

Toraks kitle koaksiyel biyopsisi için yardımcı olacak açı kalibrasyon uygulaması.

> **Araştırma prototipi.** Klinik karar için tek başına kullanılmaz; iğne konumu her zaman BT kontrolüyle doğrulanmalıdır.

## Ne yapar

1. **Plan:** Radyolog BT konsolunda ölçtüğü değerleri girer:
   - Cilt giriş noktasının orta hatta uzaklığı ve tarafı
   - Kitlenin orta hatta uzaklığı ve tarafı
   - Giriş seviyesinden kitleye dikey (masaya dik) derinlik
   - İsteğe bağlı kranyokaudal kesit farkı

   Uygulama aksiyel açıyı, kranyokaudal açıyı ve iğne yolu uzunluğunu hesaplar. Derinlik ve aksiyel açı istenirse elle de girilebilir; elle girilen değer hesaplananın yerine geçer, hesaplanan da yanında gösterilir.
2. **Masa kalibrasyonu:** Telefon ekranı yukarı, üst kenarı hastanın başına doğru olacak şekilde masaya konur. Masanın eğimi ölçülür; açılar gerçek dikeye göre değil, BT görüntüsünün dikeyine (masaya dik eksene) göre hesaplanır.
3. **Yönlendirme:** Telefonun uzun kenarı iğneye hizalanır; alt kenar iğne ucu tarafında, ekran masanın ayak ucuna bakar. Ekran düzlemi aksiyel düzlemdir (gantri lazeriyle hizalanabilir) ve görünüm BT aksiyel görüntüsüyle aynı yöndedir. Canlı açı hedefle karşılaştırılır, tolerans içine girilince ekran yeşile döner ve titreşim verilir.

## Koordinat kuralları

Masanın ayak ucundan, BT aksiyel görüntüsünün bakış yönünden bakılır:

- `x` + = ekranın sağı = **hastanın solu**
- `z` + = kaudal (ayak tarafı)
- açılar masaya dik eksenden ölçülür

Aksiyel açı = `atan2(Δx, dikey derinlik)`, iğne yolu = `√(Δx² + d² + Δz²)`.

Sensör tarafında yalnızca yerçekimi (ivmeölçer + jiroskop füzyonu) kullanılır. Manyetometre BT odasında güvenilmez olduğu için kullanılmaz. Ayrıntılı türetim `geometry/Geometry.kt` içindedir.

## Derleme

Android Studio (Ladybug veya üstü) ile açın. Kotlin + Jetpack Compose, minSdk 26.

```
./gradlew assembleDebug
./gradlew test        # geometri birim testleri
```
