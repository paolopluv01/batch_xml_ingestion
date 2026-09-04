package com.greyshield.assistance;

import com.greyshield.batch.model.generated.Enterprise;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.sql.Timestamp;


@Configuration
public class BatchConfiguration {
    /* 1 -> Reader for Enterprise items */
    @Bean
    public ItemReader<Enterprise> enterpriseReader(
            AssistanceXmlReader assistanceXmlReader) {

        return new ItemReader<>() {
            private boolean alreadyRead;

            @Override
            public Enterprise read() throws Exception {
                if (alreadyRead) {
                    return null;
                }

                alreadyRead = true;
                return assistanceXmlReader.read();
            }
        };
    }
    /* 2 -> Processor for Enterprise items */
    @Bean 
    public ItemProcessor<Enterprise, Enterprise> enterpriseProcessor() {
        return enterprise -> {
            // Perform any processing logic here if needed
            return enterprise;
        };
    }
    /* 3 -> Writer for Enterprise items */
    @Bean
    public ItemWriter<Enterprise> enterpriseWriter(
            JdbcTemplate jdbcTemplate,
            Marshaller enterpriseMarshaller) {
        return enterprises -> {
            for (Enterprise enterprise : enterprises) {
                System.out.println("Writing Enterprise to database: " + enterprise.getSystem());

                StringWriter xml = new StringWriter();
                enterpriseMarshaller.marshal(enterprise, xml);

                jdbcTemplate.update(
                        "INSERT INTO ENTERPRISES (SYSTEM_NAME, EXPORT_DATE, XML_DATA) VALUES (?, ?, ?)",
                        enterprise.getSystem(),
                    Timestamp.from(enterprise.getExportDate()
                        .toGregorianCalendar()
                        .toInstant()),
                        xml.toString());

                enterprise.getSuppliers().getSupplier().forEach(supplier ->
                        jdbcTemplate.update(
                                "INSERT INTO SUPPLIERS (SUPPLIER_ID, NAME, RATING) VALUES (?, ?, ?)",
                                supplier.getId(),
                                supplier.getName(),
                                supplier.getRating()));

                enterprise.getComponents().getComponent().forEach(component ->
                    jdbcTemplate.update(
                        "INSERT INTO COMPONENTS (COMPONENT_ID, SUPPLIER_REF, NAME, QUALITY_CLASS) VALUES (?, ?, ?, ?)",
                        component.getId(),
                        component.getSupplierRef(),
                        component.getName(),
                        component.getQualityClass()));

                enterprise.getStock().getLocation().forEach(location ->
                    jdbcTemplate.update(
                        "INSERT INTO STOCK (ZONE, COMPONENT_REF, QUANTITY, REORDER_LEVEL) VALUES (?, ?, ?, ?)",
                        location.getZone(),
                        location.getComponentRef(),
                        location.getQuantity().intValueExact(),
                        location.getReorderLevel().intValueExact()));

                enterprise.getValuations().getAsset().forEach(asset ->
                    jdbcTemplate.update(
                        "INSERT INTO VALUATIONS (COMPONENT_REF, PRODUCTION_COST, PRODUCTION_CURRENCY, RETAIL_PRICE, RETAIL_CURRENCY, TAX_RATE) VALUES (?, ?, ?, ?, ?, ?)",
                        asset.getComponentRef(),
                        new BigDecimal(asset.getProductionCost().getContent()),
                        asset.getProductionCost().getCurrency(),
                        new BigDecimal(asset.getRetailPrice().getContent()),
                        asset.getRetailPrice().getCurrency(),
                        asset.getTaxRate()));
            }
        };
    }

    @Bean
    public Marshaller enterpriseMarshaller() throws JAXBException {
        Marshaller marshaller = jakarta.xml.bind.JAXBContext
                .newInstance(Enterprise.class)
                .createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        return marshaller;
    }
    /* 4 -> Configuration for Step and Job beans for processing Enterprise items*/
    @Bean
    public Step enterpriseStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ItemReader<Enterprise> enterpriseReader,
            ItemProcessor<Enterprise, Enterprise> enterpriseProcessor,
            ItemWriter<Enterprise> enterpriseWriter) {

        return new StepBuilder("enterpriseStep", jobRepository)
                .<Enterprise, Enterprise>chunk(1)
                .reader(enterpriseReader)
                .processor(enterpriseProcessor)
                .writer(enterpriseWriter)
                .transactionManager(transactionManager)
                .build();
    }
    // 5 -> Configuration for Job bean for processing Enterprise items
    @Bean
    public Job enterpriseJob(JobRepository jobRepository, Step enterpriseStep) {
        return new JobBuilder("enterpriseJob", jobRepository)
                .start(enterpriseStep)
                .build();
    }

    // 6. VERIFICA POST-ESECUZIONE: Stampa a terminale
    @Bean
    public ApplicationRunner controllaDatabase(JdbcTemplate jdbcTemplate) {
        return args -> {
            System.out.println("\n=========================================");
            System.out.println("VERIFICA SALVATAGGIO IN H2 (FINE BATCH):");
            System.out.println("=========================================");
            
                    jdbcTemplate.queryForList("SELECT ID, SYSTEM_NAME, EXPORT_DATE, XML_DATA FROM ENTERPRISES")
                        .forEach(row -> System.out.println(
                            "Presente in DB -> ID: " + row.get("ID") +
                            " | System: " + row.get("SYSTEM_NAME") + " | Export Date: " + row.get("EXPORT_DATE") + " | XML Data: " + row.get("XML_DATA")));

                    System.out.println("FORNITORI INSERITI:");
                    jdbcTemplate.queryForList("SELECT SUPPLIER_ID, NAME, RATING FROM SUPPLIERS")
                        .forEach(row -> System.out.println(
                            "Supplier -> ID: " + row.get("SUPPLIER_ID") +
                            " | Name: " + row.get("NAME") +
                            " | Rating: " + row.get("RATING")));

                    System.out.println("COMPONENTI INSERITI:");
                    jdbcTemplate.queryForList("SELECT COMPONENT_ID, SUPPLIER_REF, NAME, QUALITY_CLASS FROM COMPONENTS")
                        .forEach(row -> System.out.println(
                            "Component -> ID: " + row.get("COMPONENT_ID") +
                            " | Supplier: " + row.get("SUPPLIER_REF") +
                            " | Name: " + row.get("NAME") +
                            " | Quality: " + row.get("QUALITY_CLASS")));

                    System.out.println("STOCK INSERITO:");
                    jdbcTemplate.queryForList("SELECT ID, ZONE, COMPONENT_REF, QUANTITY, REORDER_LEVEL FROM STOCK")
                        .forEach(row -> System.out.println(
                            "Stock -> ID: " + row.get("ID") +
                            " | Zone: " + row.get("ZONE") +
                            " | Component: " + row.get("COMPONENT_REF") +
                            " | Quantity: " + row.get("QUANTITY") +
                            " | Reorder level: " + row.get("REORDER_LEVEL")));

                    System.out.println("VALUTAZIONI INSERITE:");
                    jdbcTemplate.queryForList("SELECT ID, COMPONENT_REF, PRODUCTION_COST, PRODUCTION_CURRENCY, RETAIL_PRICE, RETAIL_CURRENCY, TAX_RATE FROM VALUATIONS")
                        .forEach(row -> System.out.println(
                            "Valuation -> ID: " + row.get("ID") +
                            " | Component: " + row.get("COMPONENT_REF") +
                            " | Production cost: " + row.get("PRODUCTION_COST") + " " + row.get("PRODUCTION_CURRENCY") +
                            " | Retail price: " + row.get("RETAIL_PRICE") + " " + row.get("RETAIL_CURRENCY") +
                            " | Tax rate: " + row.get("TAX_RATE")));

            System.out.println("=========================================\n");
        };
    }

}
