1. 从服务器拷配置（示例）：
   scp root@VM:/etc/nginx/sites-enabled/grainservice.club ./deploy/nginx/sites-enabled.grainservice.club.bak
   若 grainservice 写在主配置里（如 /etc/nginx/nginx.conf 的 server { }），则拷整份 nginx.conf 到本仓库
   deploy/nginx-server-backup/nginx/nginx.conf — 该文件已合并静态图片 location，可直接对照上传。

2. 编辑 sites-enabled 或 nginx.conf 里对应 grainservice.club 的 server { }：
   - 打开 deploy/nginx/static-images-locations.conf
   - 把其中所有 location 块粘贴到 server 内
   - 务必放在「location / { try_files $uri $uri/ /index.html; }」或类似 SPA 规则之前

3. 若图片实际不在 /opt/tomcat/latest/app-data/images/，请全文替换该路径。

4. 若上传接口返回 413 Request Entity Too Large：在对应 server { } 内增加（或与 Spring 单次上传上限一致调大）：
   client_max_body_size 100m;
   示例见 deploy/nginx-server-backup/nginx/nginx.conf 中 grainservice.club 的 server 块。

5. 检查并重载：
   nginx -t && systemctl reload nginx

6. 用 curl 验证应为 image/jpeg 而非 text/html：
   curl -I "https://grainservice.club/goodsImage/某已有文件名.jpg"

GET 路径列表见同目录 GET-image-urls.txt
