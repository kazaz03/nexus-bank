# Kako pokrenuti aplikaciju (Task 8)

Ovo je brzi cheat-sheet.

## Preduslovi
- Docker Desktop pokrenut
- Java 17, Maven (mvnw je već u repo-u)
- Node.js + npm (već imaš)

## Pokretanje (8 koraka — svaki u zaseban PowerShell prozor)

Svi koraci od 2 do 7 imaju isti pattern: uđi u folder servisa i pokreni
`.\mvnw.cmd spring-boot:run`. Za frontend (korak 8) je `npm start`.

| # | Šta | Komande |
| - | --- | --- |
| 1 | RabbitMQ (novo) | `docker compose -f compose-rabbitmq.yaml up -d` |
| 2 | Eureka discovery (8761) | `cd discovery-server` → `.\mvnw.cmd spring-boot:run` |
| 3 | User service (8081) | `cd user-service` → `.\mvnw.cmd spring-boot:run` |
| 4 | Account service (8082) | `cd account-service` → `.\mvnw.cmd spring-boot:run` |
| 5 | Transaction service (8083) | `cd transaction-service` → `.\mvnw.cmd spring-boot:run` |
| 6 | Loan service (8084) | `cd loan-service` → `.\mvnw.cmd spring-boot:run` |
| 7 | API Gateway (8080) | `cd api-gateway` → `.\mvnw.cmd spring-boot:run` |
| 8 | Angular frontend (4200) | `cd frontend` → `npm install` (samo prvi put) → `npm start` |

Svaki backend servis je spreman kada vidiš `Started <Naziv>Application in X seconds`.
Frontend je spreman kada vidiš `Application bundle generation complete`. Nakon
toga otvori **http://localhost:4200** u browseru.

## Korisne stranice
- Angular app: http://localhost:4200
- API Gateway: http://localhost:8080 (sve API rute idu kroz ovo)
- Eureka dashboard: http://localhost:8761
- RabbitMQ Management: http://localhost:15672 (guest / guest)
- Swagger po servisu: http://localhost:8081/swagger-ui.html (i 8082, 8083, 8084)

## Test korisnici (u user-service DataLoader)

| Email | Lozinka | Uloga |
| --- | --- | --- |
| `admin@nexusbank.com` | `admin123` | ADMIN |
| `ana.kovacevic@nexusbank.com` | `password2` | TELLER |
| `edin.hasanovic@nexusbank.com` | `password3` | LOAN_OFFICER |
| `marko.nikolic@nexusbank.com` | `password1` | CUSTOMER |

## Demo F14 saga (RabbitMQ async flow)

1. Otvori http://localhost:4200.
2. Loginuj se kao `edin.hasanovic@nexusbank.com` / `password3`.
3. Idi na **Loan queue** taban.
4. Klikni **Approve** na nekoj PENDING aplikaciji.
5. Status u tabeli prvo postaje `APPROVED` (intermediate). Klikni **Refresh**
   nakon par sekundi — status se mijenja u `DISBURSED` (finalni).
6. Otvori RabbitMQ Management UI (http://localhost:15672) → tab Queues — vidjet
   ćeš da su poruke prošle kroz `loan.approved.queue` i
   `loan.disbursement.completed.queue`.

## Demo neuspješne sage (inverzna akcija)

1. U bazi account-service (port 3307), ručno zatvori račun klijenta 1
   (`UPDATE accounts SET status='CLOSED' WHERE id = 1;`).
2. Approve PENDING aplikaciju za customerId=1 / accountId=1.
3. Account service neće moći credit-ovati, publish-uje
   `loan.disbursement.failed`.
4. Loan service prima poruku, vraća status na `REJECTED` sa razlogom u
   `rejection_reason` koloni. Time je obnovljeno konzistentno stanje između
   dvije baze.

## Zaustavljanje

Ctrl+C u svakom PowerShell prozoru. RabbitMQ kontejner zaustaviti sa:
```powershell
docker compose -f compose-rabbitmq.yaml down
```
