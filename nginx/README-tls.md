# HTTPS для startlift.duckdns.org

nginx из этого compose — единая точка входа:

- `https://startlift.duckdns.org/` → фронтенд (`starlift-front`, хост:8080)
- `https://startlift.duckdns.org/api/` → бэкенд (`app:8080`)
- `http://…` → 301 на HTTPS (кроме `/.well-known/acme-challenge/` и `/healthz`)

Сертификат Let's Encrypt выпускает certbot на хосте и кладёт в `/etc/letsencrypt`,
nginx монтирует его read-only. Продление — systemd-таймер `certbot.timer`
(webroot `/var/www/certbot`), после продления nginx перечитывает конфиг через
deploy-hook `/etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh`.

Проверить продление: `sudo certbot renew --dry-run`.

Первичный выпуск на новом сервере (nginx ещё без сертификата не стартует):

```sh
sudo apt install certbot && sudo mkdir -p /var/www/certbot
docker stop java-speaker-club-nginx
sudo certbot certonly --standalone -d startlift.duckdns.org --agree-tos --register-unsafely-without-email
docker compose up -d nginx
```
