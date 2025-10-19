# Zano DEX Trading (Market Making) Bot

This repository contains a Docker-based deployment of the Zano market making bot. The bot connects to a running Zano node and wallet, reads trading configuration from `config.yaml`, stores state in MariaDB, and exposes a REST API for monitoring. The instructions below target **Ubuntu 24.04 LTS** and cover two common scenarios:

1. Running everything on your own workstation for development or evaluation.
2. Deploying on a public web server with Apache acting as a reverse proxy in front of Docker and TLS certificates managed by Let's Encrypt.

> ⚠️  The bot will only trade with the funds that exist in the configured wallets. It will not mint or issue new tokens.

---

## 1. Local Workstation Setup (no reverse proxy)

### 1.1. Install prerequisites

```bash
sudo apt update
sudo apt install -y git docker.io docker-compose openjdk-21-jdk maven
sudo usermod -aG docker "$USER"
newgrp docker
```

### 1.2. Clone the repository

```bash
cd ~/projects  # or another working directory
git clone https://github.com/your-org/MMbotDocker.git
cd MMbotDocker
```

### 1.3. Prepare Zano data folders

```bash
mkdir -p zano-data zano-wallet db
sudo chown -R 65532:65532 ./zano-data && sudo chmod 700 ./zano-data
sudo chown -R 65532:65532 ./zano-wallet && sudo chmod 700 ./zano-wallet

```

### 1.4. Configure the bot

1. Generate a JWT key for the bot:
   ```bash
   python3 createKey.py localhost ./keys/
   ```
   
### 1.5. Build and launch the stack

```bash
docker-compose build
```

The services are now built and can be started. If you run without -d then you will see the full log and can close it by pressing CTRL+c.

Important: At first the Zano daemon will download a snapshot of the blockchain, during this time, the wallet will show "[RPC0][ERROR] Location:", "HROW EXCEPTION: error::no_connection_to_daemon".
This is normal and will disappear as soon as Zano has downloaded the snapshot and connected to the Zano network to download the rest of the blocks.

```bash
docker-compose up -d
```

You now have:

- Zano node and wallet containers running inside the `backend` Docker network.
- A MariaDB 11 container with the `marketmaker` database auto-initialized from `db/init.sql`.
- The market making bot accessible on `http://localhost:8084`.

Logs can be inspected with:

```bash
docker compose logs -f app
```

Stop the environment with:

```bash
docker-compose down
```

---

## 2. Web Server Deployment (Apache reverse proxy + Let's Encrypt)

### 2.1. Install system packages

```bash
sudo apt update
sudo apt install -y git docker.io docker-compose apache2 apache2-utils certbot python3-certbot-apache openjdk-21-jdk maven
sudo usermod -aG docker "$USER"
```

Log out and back in (or run `newgrp docker`) so your user can run Docker commands.

### 2.2. Clone and configure the project

```bash
sudo mkdir -p /opt/mmbot && sudo chown $USER:$USER /opt/mmbot
cd /opt/mmbot
git clone https://github.com/your-org/MMbotDocker.git
cd MMbotDocker
```

### 1.3. Prepare Zano data folders

```bash
mkdir -p zano-data zano-wallet db
sudo chown -R 65532:65532 ./zano-data && sudo chmod 744 ./zano-data
sudo chown -R 65532:65532 ./zano-wallet && sudo chmod 744 ./zano-wallet
```

Adjust docker-compose.yml` as needed (database passwords, wallet passwords, CoinEx API keys, domain names, etc.). Generate the JWT signing key with:

```bash
python3 createKey.py tradebot.example.com ./keys/
```

### 2.3. Build and run the Docker services

```bash
docker-compose build
docker-compose up -d
```

Verify services are running:

```bash
docker-compose ps
```

See the logs and Zano sync status.

Important: At first the Zano daemon will download a snapshot of the blockchain, during this time, the wallet will show "[RPC0][ERROR] Location:", "HROW EXCEPTION: error::no_connection_to_daemon".
This is normal and will disappear as soon as Zano has downloaded the snapshot and connected to the Zano network to download the rest of the blocks.

```bash
docker-compose logs -f
```

Ensure the bot's API responds on the internal port:

```bash
curl http://127.0.0.1:8084/api/application-status
```

### 2.4. Configure Apache as a reverse proxy

Enable the necessary Apache modules:

```bash
sudo a2enmod proxy proxy_http proxy_wstunnel ssl headers rewrite
sudo systemctl restart apache2
```

Create an HTTP Basic Auth credential store so only authenticated users can reach the bot UI. Replace `admin` with the username
you want to use. The `-c` flag creates the file; omit it when adding additional accounts in the future:

```bash
sudo htpasswd -c /etc/apache2/tradebot.htpasswd admin
```

Create a new Apache site definition (replace `tradebot.example.com` with your domain):

```bash
sudo tee /etc/apache2/sites-available/tradebot.conf > /dev/null <<'APACHE'
<VirtualHost *:80>
    ServerName tradebot.example.com

    ProxyPreserveHost On
    ProxyPass / http://127.0.0.1:8084/
    ProxyPassReverse / http://127.0.0.1:8084/

    <Location "/">
        AuthType Basic
        AuthName "TradeBot Admin"
        AuthUserFile /etc/apache2/tradebot.htpasswd
        Require valid-user
    </Location>

    ErrorLog ${APACHE_LOG_DIR}/tradebot-error.log
    CustomLog ${APACHE_LOG_DIR}/tradebot-access.log combined
</VirtualHost>
APACHE
```

Enable the site:

```bash
sudo a2ensite tradebot.conf
sudo systemctl reload apache2
```

At this point your site will respond on HTTP and require the credentials you configured. Continue to secure it with TLS.

### 2.5. Obtain and install a Let's Encrypt certificate

Run the Certbot Apache installer:

```bash
sudo certbot --apache -d tradebot.example.com
```

Follow the prompts to:

1. Provide a contact email address.
2. Agree to the terms of service.
3. Select option `2` to redirect all traffic to HTTPS.

Certbot will automatically edit the Apache virtual host to terminate TLS and forward requests to the Dockerized bot. It will copy over the Basic Auth directives, but verify them inside `/etc/apache2/sites-enabled/tradebot-le-ssl.conf`.

Renewals are handled automatically via the `certbot.timer` systemd unit, but you can test the process with:

```bash
sudo certbot renew --dry-run
```

### 2.6. Managing the deployment

- View container status: `docker-compose ps`
- Tail logs: `docker-compose logs -f`
- Restart the stack: `docker-compose restart`
- Update to the latest bot version:
  ```bash
  cd /opt/mmbot/MMbotDocker
  git pull
  mvn clean install
  docker-compose build ; docker-compose down ; docker-compose up 
  ```

If you need the services to start automatically on reboot, enable Docker's systemd unit:

```bash
sudo systemctl enable docker
```

---

## Additional Notes

- If you get "KeyError: 'ContainerConfig'", try to run docker-compose down
- Ensure the Zano blockchain synchronizes fully before starting live trading. The bot will pause until the node reports full sync.
- MariaDB credentials in `docker-compose.yml` are for development convenience. Change them to strong, unique passwords in production.
- Back up the `keys/` directory and wallet files (`zano-wallet/`) securely. Losing them will prevent the bot from authenticating or accessing funds.

With these steps the Zano market making bot can be run either locally for testing or on a hardened Ubuntu 24.04 server with HTTPS and an Apache reverse proxy.