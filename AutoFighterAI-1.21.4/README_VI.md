# Auto Fighter AI GUI — Fabric 1.21.4

## Mở GUI
Vào thế giới rồi nhập lệnh `/autofighterai` trong chat.

## Ba chức năng bật/tắt độc lập
1. **Chiến đấu tự động**: tìm mob thù địch trong 20 block, hướng về thân mob, tiến tới tầm cận chiến và đánh; 30% cơ hội nhảy khi bắt đầu đòn.
2. **Né đòn**: khi bị trúng đòn và mob ở gần, di chuyển ngang ngắn.
3. **Tự ăn khi máu thấp**: khi còn 10 HP trở xuống và đói, ăn thức ăn từ hotbar, tối đa 2 lượt cho đến khi tắt/bật lại tính năng.

Chỉ bật chức năng cần thiết; cả ba có thể cùng bật. Phím **G** là nút bật/tắt nhanh cho chiến đấu tự động.

## Build / cài đặt
Build bằng Java 21 + Gradle 8.10.2: `gradle --no-daemon clean build`.
JAR sau remap nằm tại `build/libs/AutoFighterAI-1.21.4-GUI-1.0.0.jar`.
Cần Minecraft 1.21.4, Fabric Loader và Fabric API tương thích. Chép JAR vào `.minecraft/mods`.

Build CI sẽ kiểm tra lớp GUI, entrypoint và cấu trúc JAR; vẫn cần thử trong game thật.
