-- ============================================
-- INSERT IGNORE: Chỉ insert nếu chưa tồn tại
-- ============================================
-- chưa có ảnh nên lấy placeholder ở  lorem piscum, text dùng markdown để fe sửa
INSERT IGNORE INTO stories(id, title, slug, content, thumbnail_url, category, view_count, is_published, published_at, created_at, updated_at) VALUES
(1, 'Hành trình tái sinh của chai nhựa', 'hanh-trinh-tai-sinh-cua-chai-nhua',
'## Một chai nhựa sống bao lâu?

Bạn có biết một chai nhựa có thể tồn tại trong môi trường từ **450 đến 1000 năm**? Thay vì vứt bỏ, chai nhựa có thể được tái sinh thành nhiều sản phẩm hữu ích.

## Hành trình của chai nhựa

1. **Bước 1:** Bạn bỏ chai nhựa vào thùng tái chế
2. **Bước 2:** Chai được thu gom, phân loại, làm sạch
3. **Bước 3:** Nghiền nhỏ thành hạt nhựa (PET flakes)
4. **Bước 4:** Hạt nhựa được nấu chảy và tạo hình mới
5. **Bước 5:** Sản phẩm mới: áo thun, thảm, chai mới, hoặc đồ nội thất

## Lợi ích của tái chế chai nhựa

- Tiết kiệm **66% năng lượng** so với sản xuất nhựa mới
- Giảm **1 tấn CO2** cho mỗi 1 tấn nhựa tái chế
- Bảo vệ đại dương và sinh vật biển

> *Hãy cùng chung tay - mỗi chai nhựa được tái chế là một hành tinh xanh hơn!*',
'https://picsum.photos/id/1/400/200', 'RECYCLE_TIPS', 0, true, NOW(), NOW(), NOW()),

(2, 'Đảo rác Thái Bình Dương - Thảm họa thầm lặng', 'dao-rac-thai-binh-duong',
'## Bạn có biết?

Giữa Thái Bình Dương tồn tại một *"lục địa rác"* rộng gấp **3 lần nước Pháp**, khoảng 1.6 triệu km²!

## Sự thật về đảo rác

- **Khối lượng:** Hơn 80.000 tấn nhựa
- **Số lượng mảnh nhựa:** ~1.800 tỷ mảnh
- **Diện tích:** 1.6 triệu km²
- **Vị trí:** Giữa Hawaii và California

## Hậu quả

- Hơn **1 triệu sinh vật biển** chết mỗi năm vì nuốt phải nhựa
- Vi nhựa xâm nhập vào chuỗi thức ăn của con người
- San hô bị bệnh do tiếp xúc với nhựa

## Giải pháp

Các tổ chức như The Ocean Cleanup đang nỗ lực dọn dẹp. Nhưng giải pháp bền vững nhất vẫn là: **GIẢM RÁC TỪ ĐẦU NGUỒN**. Hãy nói không với nhựa dùng một lần!',
'https://picsum.photos/id/10/400/200', 'ENVIRONMENT_IMPACT', 0, true, NOW(), NOW(), NOW()),

(3, 'Bé gái 9 tuổi và sáng kiến đổi rác lấy cây', 'be-gai-9-tuoi-doi-rac-lay-cay',
'## Câu chuyện truyền cảm hứng từ Nadia Sparkes

Nadia Sparkes, **9 tuổi** đến từ Anh, đã khởi xướng chiến dịch *"Trash to Trees"* - đổi rác lấy cây xanh.

## Ý tưởng đơn giản mà hiệu quả

- Thu gom 1 túi rác → nhận **1 cây giống**
- Mỗi cây được trồng góp phần xanh hóa hành tinh

## Thành quả sau 2 năm

- Hơn **10.000 túi rác** được thu gom
- **5.000 cây xanh** được trồng mới
- Cả cộng đồng tham gia, thay đổi nhận thức về rác thải

## Bài học

Tuổi tác không quan trọng. Một ý tưởng nhỏ có thể tạo ra thay đổi lớn. Nếu bé gái 9 tuổi có thể làm được, tại sao chúng ta không thể?

*Hãy bắt đầu từ những hành động nhỏ nhất hôm nay!*',
'https://picsum.photos/id/20/400/200', 'INSPIRATION', 0, true, NOW(), NOW(), NOW()),

(4, 'Rác thải điện tử - Mỏ vàng ngầm', 'rac-thai-dien-tu-mo-vang-ngam',
'## Rác thải điện tử: Vấn đề toàn cầu

Mỗi năm thế giới thải ra hơn **50 triệu tấn rác điện tử** - nặng bằng 4.500 tháp Eiffel!

## Nhưng... rác điện tử KHÔNG phải rác!

Trong rác điện tử chứa:

- **Vàng:** 1 tấn điện thoại cũ chứa 300g vàng (gấp 30 lần quặng vàng tự nhiên)
- **Bạc, đồng, bạch kim, paladi** - giá trị lên đến hàng tỷ đô la

## Công nghệ tái chế hiện đại

- Thu hồi đến **95% kim loại quý** từ bo mạch điện tử
- Tái chế nhựa điện tử thành vỏ điện thoại mới, đồ gia dụng
- Xử lý an toàn các chất độc hại (chì, thủy ngân, cadmium)

## Bạn có thể làm gì?

- Không vứt pin, điện thoại, laptop vào thùng rác thường
- Đem đến **điểm thu gom rác điện tử**
- Chọn mua thiết bị có thiết kế dễ tái chế, dễ sửa chữa',
'https://picsum.photos/id/0/400/200', 'INNOVATION', 0, true, NOW(), NOW(), NOW()),

(5, 'Chuyến du lịch zero-waste của gia đình Việt', 'du-lich-zero-waste-gia-dinh-viet',
'## Hành trình 30 ngày, 10 tỉnh thành, 0 rác thải nhựa

Gia đình anh Tuấn (Hà Nội) đã thực hiện chuyến du lịch xuyên Việt mà hầu như không tạo ra rác thải nhựa.

## Bí quyết của họ

- **Mang theo bộ đồ dùng cá nhân:** chai nước inox, ống hút tre, hộp cơm, túi vải
- **Mua sắm thông minh:** ưu tiên chợ địa phương, mang hộp đựng thức ăn
- **Từ chối đồ nhựa dùng một lần:** nói *"không"* với ống hút nhựa, cốc nhựa, túi nilon

## Kết quả

- Chỉ tạo ra **1 túi rác nhỏ** sau 30 ngày (chủ yếu là nilong bánh kẹo không thể từ chối)
- Tiết kiệm hơn **2 triệu đồng** so với dùng đồ nhựa dùng một lần
- Các con học được lối sống có trách nhiệm với môi trường

> *Du lịch xanh không khó! Hãy bắt đầu từ chuyến đi tiếp theo của bạn.*',
'https://picsum.photos/id/15/400/200', 'TIPS', 0, true, NOW(), NOW(), NOW()),

(6, 'Lon nhôm có thể tái chế mãi mãi', 'lon-nhom-tai-che-mai-mai',
'## Siêu năng lực của lon nhôm

Lon nhôm là vật liệu có khả năng tái chế **VÔ HẠN** mà không bị giảm chất lượng. Một lon nhôm hôm nay có thể tái sinh thành lon mới chỉ sau **60 ngày**!

## Số liệu ấn tượng

- Tái chế 1 lon nhôm tiết kiệm năng lượng đủ để xem TV trong **3 giờ**
- **75%** lượng nhôm từng được sản xuất vẫn đang được sử dụng đến ngày nay
- Ngành công nghiệp tái chế nhôm tiết kiệm **95% năng lượng** so với sản xuất nhôm mới

## Lon nhôm tái chế thành

- Lon nước giải khát mới
- Khung cửa sổ, khung xe đạp
- Vỏ máy bay, vỏ tên lửa
- Đồ dùng nhà bếp

## Hãy nhớ

Không cần rửa sạch lon, chỉ cần đổ hết chất lỏng và thả vào thùng tái chế. **Mỗi lon nhôm được tái chế là một hành tinh xanh hơn!**',
'https://picsum.photos/id/30/400/200', 'RECYCLE_TIPS', 0, true, NOW(), NOW(), NOW()),

(7, 'Rác thải y tế sau đại dịch', 'rac-thai-y-te-sau-dai-dich',
'## COVID-19 và cuộc khủng hoảng rác thải y tế

Đại dịch COVID-19 đã tạo ra lượng rác thải y tế khổng lồ: ước tính **8 triệu tấn** khẩu trang, găng tay, áo choàng y tế trên toàn cầu.

## Con số biết nói

- **3 tỷ** khẩu trang bị vứt bỏ MỖI NGÀY trong đại dịch
- **65 tỷ** găng tay dùng một lần
- 1.6 tỷ tấn rác thải y tế từ các bệnh viện

## Hậu quả môi trường

- Khẩu trang trôi ra biển - đe dọa sinh vật biển (cá, rùa, chim)
- Lây lan bệnh do rác y tế không được xử lý đúng cách
- Vi nhựa từ khẩu trang thấm vào nguồn nước, đất

## Giải pháp

- Chuyển sang đồ bảo hộ tái sử dụng thay vì dùng một lần
- Đầu tư công nghệ xử lý rác y tế không phát thải
- Mỗi người dân: **cắt dây đeo khẩu trang** trước khi vứt (để tránh động vật mắc kẹt)',
'https://picsum.photos/id/42/400/200', 'ENVIRONMENT_IMPACT', 0, true, NOW(), NOW(), NOW()),

(8, 'Startup biến rác thải nhựa thành gạch xây dựng', 'startup-bien-rac-nhua-thanh-gach',
'## Conceptos Plásticos - Giải pháp đến từ Colombia

Startup Conceptos Plásticos đã phát minh ra công nghệ biến rác thải nhựa thành **gạch xây dựng** - bền hơn, rẻ hơn, thân thiện với môi trường.

## Công nghệ hoạt động thế nào?

1. Thu gom rác thải nhựa (PE, PP, PS, ABS)
2. Nghiền nhỏ, trộn với phụ gia
3. Ép thành gạch (không cần nung - tiết kiệm năng lượng)

## Ưu điểm của gạch nhựa tái chế

- Chịu lực gấp **5 lần** gạch bê tông truyền thống
- Cách nhiệt, cách âm tốt hơn
- Nhẹ hơn **50%**, dễ vận chuyển
- Tuổi thọ **500+ năm** (không mục, không nứt)
- Chi phí thấp hơn **30%**

## Thành tựu

- Xây dựng hơn **500 ngôi nhà** cho người nghèo
- Tái chế hơn **2.000 tấn nhựa** mỗi năm
- Được UNDP công nhận là giải pháp sáng tạo toàn cầu

> *Nhựa không phải là kẻ thù - nếu chúng ta biết cách sử dụng nó đúng cách!*',
'https://picsum.photos/id/50/400/200', 'INNOVATION', 0, true, NOW(), NOW(), NOW()),

(9, '10 phút mỗi ngày - Thay đổi thói quen sống xanh', '10-phut-moi-ngay-song-xanh',
'## Bạn không cần trở thành nhà hoạt động môi trường để bảo vệ hành tinh!

Chỉ với **10 phút mỗi ngày**, bạn có thể tạo ra thay đổi lớn. Dưới đây là thử thách 7 ngày:

## Ngày 1: Phân loại rác (5 phút)

Làm quen với 3 thùng rác: **Tái chế, hữu cơ, rác khác**. Phân loại đúng giúp 90% rác được tái sử dụng!

## Ngày 2: Mang túi vải khi đi chợ (2 phút)

2 túi vải = 10.000 túi nilon trong 5 năm!

## Ngày 3: Từ chối ống hút nhựa (1 phút)

Nói *"Tôi không cần ống hút nhé!"* - Mỗi ngày, 500 triệu ống hút nhựa được thải ra môi trường.

## Ngày 4: Sử dụng chai nước cá nhân (1 phút)

Đổ nước vào chai inox trước khi ra khỏi nhà - tiết kiệm tiền, giảm rác nhựa.

## Ngày 5: Tái sử dụng hộp đựng thực phẩm (2 phút)

Hũ thủy tinh, hộp nhựa cũ có thể đựng đồ khô, gia vị, đồ ăn trưa.

## Ngày 6: Ủ phân compost từ rác thực phẩm (10 phút)

Vỏ trái cây, rau củ thừa + đất + 2 tháng = **phân bón hữu cơ** cho cây.

## Ngày 7: Chia sẻ hành trình (5 phút)

Kể với bạn bè, đăng lên mạng xã hội - lan tỏa thói quen sống xanh!

> *10 phút mỗi ngày = 60 giờ mỗi năm = HÀNH TINH XANH HƠN!*',
'https://picsum.photos/id/100/400/200', 'TIPS', 0, true, NOW(), NOW(), NOW()),

(10, 'Hành trình của chiếc túi nilon: 500 năm phân hủy', 'hanh-trinh-cua-tui-nilon',
'## Chuyến đi 500 năm của một chiếc túi nilon

Hãy tưởng tượng: Một chiếc túi nilon được sinh ra trong **1 giây**, nhưng phải mất **500 năm** để biến mất hoàn toàn khỏi hành tinh.

## Hành trình

1. **1 giây:** Túi nilon được sản xuất từ dầu mỏ, khí tự nhiên
2. **15 phút:** Bạn nhận túi ở siêu thị, đựng đồ, mang về nhà
3. **1 tuần:** Bạn vứt túi vào thùng rác
4. **50 năm:** Túi nilon nằm trong bãi rác, bắt đầu phân hủy thành hạt vi nhựa
5. **100 năm:** Vi nhựa thấm vào nguồn nước ngầm, đất, không khí
6. **500 năm:** Túi nilon gốc phân hủy hoàn toàn (hạt vi nhựa vẫn còn tồn tại tiếp)

## Sự thật đáng sợ

- Việt Nam: **2.500 tấn** túi nilon thải ra MỖI NGÀY
- Toàn cầu: **1 triệu túi nilon** được sử dụng MỖI PHÚT
- Chỉ **1%** túi nilon được tái chế

## Giải pháp: GIẢM THIỂU tối đa

- Mang túi vải / giỏ đi chợ
- Nói **KHÔNG** với túi nilon dù chỉ 1 cái
- Dùng túi giấy, túi phân hủy sinh học thay thế
- Tái sử dụng túi nilon cũ làm túi đựng rác

> *Mỗi chiếc túi nilon không được sử dụng là một năm ánh sáng cho hành tinh!*',
'https://picsum.photos/id/200/400/200', 'EDUCATION', 0, true, NOW(), NOW(), NOW());