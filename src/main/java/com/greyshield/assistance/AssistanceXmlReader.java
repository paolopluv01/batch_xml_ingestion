package com.greyshield.assistance;

import com.greyshield.batch.model.generated.Enterprise;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
/**
 * Service class to read the Enterprise XML file and unmarshal it into an Enterprise object.
 */
@Service
public class AssistanceXmlReader {

    public Enterprise read() throws Exception {
        JAXBContext context = JAXBContext.newInstance(Enterprise.class);
        Unmarshaller unmarshaller = context.createUnmarshaller();

        try (var inputStream =
                     new ClassPathResource("assistenza.xml").getInputStream()) {
            return (Enterprise) unmarshaller.unmarshal(inputStream);
        }
    }
}