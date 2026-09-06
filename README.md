# Assistance

Applicazione Spring Boot per leggere `assistenza.xml`, convertirlo in oggetti Java tramite JAXB e renderlo disponibile a una pipeline Spring Batch.

## Struttura del progetto

```text
src/main/
├── java/com/greyshield/assistance/
│   ├── AssistanceApplication.java -> Punto d'ingresso, classe Main.
│   ├── AssistanceXmlReader.java -> Parsing Xml con JAXB
│   └── BatchConfiguration.java -> ItemObjects e configurazioni.
|           |__ ItemReader -> legge l'oggetto Enterprise.
|           |__ ItemProcessor -> elaborazione dati.
|           |__ ItemWriter -> salvataggio XML e dati relazionali nel Db.
└── resources/
    ├── assistenza.xml
    ├── schema.sql -> schema delle tabelle applicative H2.
    └── schemas/
        ├── assistenza.xsd
        ├── supplier.xsd
        ├── inventory.xsd
        ├── warehouse.xsd
        └── finance.xsd
```

## Struttura XML

Il documento radice appartiene al namespace enterprise e contiene quattro domini:

```text
enterprise
├── suppliers       (supplier)
├── components      (component)
├── stock           (location)
└── valuations      (asset)
```

I namespace utilizzati sono:

| Dominio | Namespace |
| --- | --- |
| Enterprise | `http://www.greyshield.com/schema/enterprise` |
| Fornitori | `http://www.greyshield.com/schema/supplier` |
| Inventario | `http://www.greyshield.com/schema/inventory` |
| Magazzino | `http://www.greyshield.com/schema/warehouse` |
| Finanza | `http://www.greyshield.com/schema/finance` |

Il file `assistenza.xsd` importa `supplier.xsd`, `inventory.xsd`, `warehouse.xsd` e `finance.xsd`. In questo modo lo schema descrive anche gli elementi interni e JAXB puo generare i relativi POJO.

## Schemi XSD

### Enterprise

`assistenza.xsd` definisce:

- `enterprise`
- gli attributi obbligatori `system` e `exportDate`
- l'ordine dei quattro blocchi di dominio

### Fornitori

`supplier.xsd` definisce:

```text
suppliers
└── supplier*
    ├── name
    └── rating
```

Ogni `supplier` richiede l'attributo `id`.

### Inventario

`inventory.xsd` definisce:

```text
components
└── component*
    ├── name
    └── qualityClass
```

Ogni `component` richiede gli attributi `id` e `supplierRef`.

### Magazzino

`warehouse.xsd` definisce:

```text
stock
└── location*
    ├── quantity
    └── reorderLevel
```

Ogni `location` richiede gli attributi `zone` e `componentRef`.

### Finanza

`finance.xsd` definisce:

```text
valuations
└── asset*
    ├── productionCost
    ├── retailPrice
    └── taxRate
```

Ogni `asset` richiede `componentRef`. I prezzi hanno anche l'attributo `currency`.

## Generazione delle classi Java

Le classi JAXB vengono generate automaticamente da Maven nella directory temporanea:

```text
target/generated-sources/jaxb/com/greyshield/batch/model/generated/
```

La directory `target` non deve essere pushata: viene ricreata da Maven tramite `./mvnw test` o `./mvnw clean package`.

Generazione pulita:

```bash
./mvnw clean generate-sources
```

Le classi generate includono, tra le altre:

```text
Enterprise.java
SuppliersType.java
SupplierType.java
ComponentsType.java
ComponentType.java
StockType.java
LocationType.java
ValuationsType.java
AssetType.java
MoneyType.java
```

Non modificare manualmente questi file: vengono ricreati da Maven quando gli XSD cambiano.

## Lettura dell'XML

`AssistanceXmlReader` usa JAXB per trasformare il documento in un oggetto `Enterprise`:

```java
Enterprise enterprise = assistanceXmlReader.read();
```

Esempio di accesso ai dati:

```java
String system = enterprise.getSystem();

enterprise.getSuppliers().getSupplier().forEach(supplier ->
        System.out.println(supplier.getName()));

enterprise.getComponents().getComponent().forEach(component ->
        System.out.println(component.getName()));

enterprise.getStock().getLocation().forEach(location ->
        System.out.println(location.getZone()));

enterprise.getValuations().getAsset().forEach(asset ->
        System.out.println(asset.getComponentRef()));
```

## Pipeline Spring Batch

`BatchConfiguration` definisce un `ItemReader<Enterprise>` che legge il documento una sola volta:

```text
assistenza.xml
    |
    v
AssistanceXmlReader
    |
    v
Enterprise
    |
    v
ItemReader<Enterprise>
    |
    v
ItemProcessor / ItemWriter
```

Il valore `null` restituito dal reader dopo la prima lettura indica a Spring Batch che non ci sono altri item da elaborare.

## Persistenza nel database

All'avvio Spring Boot esegue `src/main/resources/schema.sql` sul database H2 embedded.
Il file crea le tabelle applicative usate dall'`ItemWriter`:

| Tabella | Contenuto | Collegamenti |
| --- | --- | --- |
| `ENTERPRISES` | sistema, data di esportazione e XML completo | tabella principale |
| `SUPPLIERS` | id, nome e rating dei fornitori | chiave `SUPPLIER_ID` |
| `COMPONENTS` | id, fornitore, nome e classe di qualita | `SUPPLIER_REF` -> `SUPPLIERS.SUPPLIER_ID` |
| `STOCK` | zona, componente, quantita e livello di riordino | `COMPONENT_REF` -> `COMPONENTS.COMPONENT_ID` |
| `VALUATIONS` | costi, prezzi, valute e aliquota fiscale | `COMPONENT_REF` -> `COMPONENTS.COMPONENT_ID` |

L'XML completo viene comunque salvato in `ENTERPRISES.XML_DATA`. In parallelo i dati dei
quattro domini vengono estratti dall'oggetto JAXB e inseriti nelle rispettive tabelle.
L'ordine di scrittura rispetta le dipendenze tra le foreign key:

```text
SUPPLIERS
        |
        v
COMPONENTS
        |
        +--> STOCK
        +--> VALUATIONS
```

### Conversioni dei tipi

- `exportDate` e definito nell'XSD come `xs:dateTime` e viene generato da JAXB come
    `XMLGregorianCalendar`; il writer lo converte in `java.sql.Timestamp` per H2.
- `quantity` e `reorderLevel` sono generati come `BigInteger` e vengono convertiti in
    `INTEGER` tramite `intValueExact()`.
- `productionCost` e `retailPrice` sono contenuti testuali in `MoneyType`; il writer li
    converte in `BigDecimal` prima dell'inserimento nelle colonne `DECIMAL`.
- Le valute (`EUR`) sono memorizzate in colonne separate rispetto agli importi.

Il `JobRepository` di Spring Batch usa invece le proprie tabelle `BATCH_*` per registrare
job, step, parametri e stati di esecuzione. Queste tabelle sono separate dalle tabelle
applicative definite in `schema.sql`.

Al termine del job, l'`ApplicationRunner` `controllaDatabase` stampa nel terminale il
record enterprise e i record inseriti in `SUPPLIERS`, `COMPONENTS`, `STOCK` e `VALUATIONS`.

## Verifica

Validazione del documento XML contro lo schema principale:

```bash
xmllint --noout \
  --schema src/main/resources/schemas/assistenza.xsd \
  src/main/resources/assistenza.xml
```

Compilazione e generazione delle classi:

```bash
./mvnw clean generate-sources
```
