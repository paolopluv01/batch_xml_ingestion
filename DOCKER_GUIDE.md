# Guida Docker - Batch XML Ingestion

Questa guida spiega come containerizzare e avviare l'applicazione Spring Boot per l'elaborazione batch di file XML.

## Prerequisiti

- **Docker**: v20.10+
- **Docker Compose**: v2.0+
- **Git** (per clonare il repository)

## Struttura dei file Docker

```
batch_xml_ingestion/
├── Dockerfile                 # Build multi-stage per la containerizzazione
├── docker-compose.yml         # Configurazione base (default)
├── docker-compose.dev.yml     # Configurazione per sviluppo
├── docker-compose.prod.yml    # Configurazione per produzione
├── .dockerignore              # File/directory da escludere dal build
└── DOCKER_GUIDE.md           # Questa guida
```

## Build dell'immagine Docker

### Build standard
```bash
docker build -t batch-xml-ingestion:latest .
```

### Build con tag specifico per versione
```bash
docker build -t batch-xml-ingestion:0.0.1 .
```

### Build senza cache
```bash
docker build --no-cache -t batch-xml-ingestion:latest .
```

## Esecuzione con Docker Compose

### 1. Configurazione di default (Sviluppo)
```bash
# Avviare il servizio
docker-compose up -d

# Visualizzare i log
docker-compose logs -f batch-app

# Fermare il servizio
docker-compose down
```

### 2. Configurazione di Sviluppo
```bash
# Avviare con file dev
docker-compose -f docker-compose.dev.yml up -d

# Visualizzare i log con follow
docker-compose -f docker-compose.dev.yml logs -f batch-app

# Fermare
docker-compose -f docker-compose.dev.yml down
```

### 3. Configurazione di Produzione
```bash
# Caricare variabili d'ambiente (opzionale)
export DB_USERNAME=admin
export DB_PASSWORD=secure_password

# Avviare in produzione
docker-compose -f docker-compose.prod.yml up -d

# Controllare lo stato
docker-compose -f docker-compose.prod.yml ps

# Fermare
docker-compose -f docker-compose.prod.yml down
```

## Accesso all'applicazione

### Spring Boot Application
- **URL**: http://localhost:8080
- **Actuator**: http://localhost:8080/actuator

### H2 Console (solo sviluppo)
- **URL**: http://localhost:8080/h2-console
- **JDBC URL**: `jdbc:h2:mem:testdb`
- **User**: `sa`
- **Password**: (lasciare vuoto)

## Variabili d'ambiente

### Variabili comuni
| Variabile | Default | Descrizione |
|-----------|---------|-------------|
| `SERVER_PORT` | 8080 | Porta del servizio |
| `SPRING_APPLICATION_NAME` | assistance | Nome applicazione |
| `JAVA_OPTS` | -Xmx512m -Xms256m | Opzioni JVM |
| `LOGGING_LEVEL_ROOT` | INFO | Livello log root |

### Database (Development)
| Variabile | Valore |
|-----------|--------|
| `SPRING_DATASOURCE_URL` | jdbc:h2:mem:testdb |
| `SPRING_DATASOURCE_DRIVERCLASSNAME` | org.h2.Driver |
| `SPRING_DATASOURCE_USERNAME` | sa |
| `SPRING_DATASOURCE_PASSWORD` | (empty) |

### Database (Production)
| Variabile | Valore |
|-----------|--------|
| `SPRING_DATASOURCE_URL` | jdbc:h2:/data/batch_db |
| `SPRING_DATASOURCE_USERNAME` | $DB_USERNAME |
| `SPRING_DATASOURCE_PASSWORD` | $DB_PASSWORD |

## Gestione dei dati

### Cartelle montate (Volumi)

#### Development
- `./src/main/resources` → `/app/resources` (read-only)
- `./data` → `/app/data`
- `./logs` → `/app/logs`

#### Production
- `/data` → `/data` (volume persistente)
- `./data/input` → `/app/data/input` (read-only)
- `./data/output` → `/app/output`
- `./data/logs` → `/app/logs`

### Prepara i dati di input
```bash
# Crea cartelle per i dati
mkdir -p data/input data/output logs

# Copia i file XML
cp src/main/resources/assistenza.xml data/input/
```

## Monitoraggio e Debug

### Visualizzare i log
```bash
# Log real-time
docker-compose logs -f batch-app

# Ultime 100 righe
docker-compose logs --tail 100 batch-app

# Log con timestamp
docker-compose logs -t batch-app
```

### Accedere al container
```bash
# Shell interattiva
docker exec -it batch_xml_ingestion_app /bin/sh

# Eseguire comandi
docker exec batch_xml_ingestion_app ls -la /app
```

### Health check
```bash
# Verificare lo stato
docker-compose ps

# Verificare i dettagli
docker inspect batch_xml_ingestion_app
```

## Ottimizzazione e Performance

### Limiti di risorse

| Ambiente | CPU | Memory |
|----------|-----|--------|
| Development | 0.5-2 | 512M-2G |
| Production | 1-2 | 1G-3G |

### Modifica manuale dei limiti
```yaml
deploy:
  resources:
    limits:
      cpus: '2'
      memory: 2G
    reservations:
      cpus: '1'
      memory: 1G
```

### JVM Tuning

#### Development
```
-Xmx512m -Xms256m
```

#### Production
```
-Xmx2g -Xms1g -XX:+UseG1GC -XX:MaxGCPauseMillis=200
```

## Troubleshooting

### Problema: Container non parte
```bash
# Verifica i log
docker-compose logs batch-app

# Verifica le risorse disponibili
docker system df
docker stats
```

### Problema: Porta già in uso
```bash
# Cambia la porta in docker-compose.yml
ports:
  - "8081:8080"  # Usa 8081 localmente

# Riavvia
docker-compose down
docker-compose up -d
```

### Problema: Dati non persistono
Verificare che i volumi siano corretti in `docker-compose.yml`:
```bash
# Visualizza i volumi
docker volume ls

# Ispeziona un volume
docker volume inspect batch_data
```

### Problema: Memoria insufficiente
```bash
# Aumenta la memoria JVM
JAVA_OPTS=-Xmx4g -Xms2g docker-compose up -d

# Oppure modifica .env o docker-compose.yml
```

## Gestione avanzata

### Ricostruire l'immagine senza cache
```bash
docker-compose build --no-cache
docker-compose up -d
```

### Push su Docker Registry
```bash
# Tag l'immagine
docker tag batch-xml-ingestion:latest username/batch-xml-ingestion:0.0.1

# Push
docker push username/batch-xml-ingestion:0.0.1
```

### Backup database (Production)
```bash
# Backup del volume
docker run --rm -v batch_data:/data -v $(pwd):/backup \
  busybox tar czf /backup/db_backup.tar.gz -C /data .

# Restore
docker run --rm -v batch_data:/data -v $(pwd):/backup \
  busybox tar xzf /backup/db_backup.tar.gz -C /data
```

### Scaling (se necessario)
```bash
# Aumenta istanze del servizio
docker-compose up -d --scale batch-app=3

# Nota: Assicurati che le porte non entrino in conflitto
```

## Pulizia

### Rimuovere container stopped
```bash
docker-compose down
```

### Rimuovere volumi (cautela!)
```bash
docker-compose down -v
```

### Rimuovere immagini
```bash
docker rmi batch-xml-ingestion:latest
```

### Cleanup completo
```bash
docker-compose down -v
docker system prune -a
```

## Best Practices

✅ **DO**:
- Usare versioni specifiche delle immagini base (`:21-jdk-alpine`)
- Definire healthcheck per ogni servizio
- Usare non-root user (appuser)
- Limitare le risorse con `deploy.resources`
- Versioning delle immagini
- Separare configurazioni dev/prod

❌ **DON'T**:
- Eseguire come root in container
- Montare cartelle non necessarie
- Usare tag `latest` in produzione
- Salvare secrets in Dockerfile
- Committare `.env` su Git

## File di riferimento

- `Dockerfile`: Multi-stage build per immagine leggera
- `docker-compose.yml`: Configurazione base
- `docker-compose.dev.yml`: Setup per sviluppo (H2 in-memory)
- `docker-compose.prod.yml`: Setup produzione (H2 file-based)
- `.dockerignore`: Esclude file non necessari dal build

## Supporto e problemi

Per problemi o domande:
1. Verifica i log: `docker-compose logs -f`
2. Consulta la documentazione di Spring Boot
3. Verifica lo spazio disco e memoria disponibile
4. Controlla la versione di Docker/Docker Compose

---

**Versione**: 1.0
**Ultima modifica**: 2026-09-14
