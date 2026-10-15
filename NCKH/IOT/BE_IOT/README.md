# HAUI-TRASH - Backend

Mô tả ngắn:
Dự án backend cho hệ thống HAUI-TRASH (Spring Boot). README này hướng dẫn cách thiết lập môi trường, cấu hình kết nối MySQL, chạy và build ứng dụng bằng IntelliJ / dòng lệnh (Maven).

## Yêu cầu
- Java 21 (JDK 21)
- Spring Boot 3.5.7
- MySQL 8.0.41
- IDE: IntelliJ IDEA (khuyến nghị)
- Build tool: Maven

## Thiết lập cơ sở dữ liệu (MySQL)
1. Khởi động MySQL 8.0.41.
2. Tạo database và user (ví dụ):
```sql

Cấu hình kết nối (application.properties / application.yml)
Ví dụ application.yml (thay username/password theo cấu hình của bạn):
Nếu dùng application.properties:

properties
spring.datasource.url=jdbc:mysql://localhost:
spring.datasource.username=haui_user
spring.datasource.password=your_password
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect
Lưu ý:

Đổi ddl-auto sang validate hoặc tắt trong môi trường production.
Nếu MySQL chạy trên host khác, cập nhật localhost thành host tương ứng.
Chạy dự án bằng IntelliJ
Mở IntelliJ → File → Open → chọn thư mục dự án.
IntelliJ sẽ import project Maven và tải dependency.
Chỉnh cấu hình Run/Debug:
Chọn main class (ví dụ com.example.Application — kiểm tra package chính trong dự án).
Set VM options nếu cần.
Chạy application bằng nút Run hoặc Run → Run 'Application'.
Chạy và build bằng Maven (dòng lệnh)
Biên dịch và chạy tests:
bash
mvn clean package
Chạy ứng dụng trực tiếp (không build jar):
bash
mvn spring-boot:run
Sau khi build, chạy jar:
bash
java -jar target/your-app-name.jar
Thay your-app-name bằng tên file jar thực tế (kiểm tra folder target).

Kiểm tra pom.xml
Đảm bảo pom.xml có các cấu hình chính sau:

parent: org.springframework.boot:spring-boot-starter-parent:3.5.7 (hoặc tương đương)
source/target compatibility: 21
dependency mysql-connector-j (phiên bản 8.x)
Ví dụ ngắn trong pom.xml:

XML
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>3.5.7</version>
  <relativePath/> <!-- lookup parent from repository -->
</parent>

<properties>
  <java.version>21</java.version>
</properties>

<dependencies>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
  </dependency>
  <dependency>
    <groupId>mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
  </dependency>
  <!-- other dependencies -->
</dependencies>