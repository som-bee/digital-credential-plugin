package io.mosip.certify.mockcsvdataprovider.integration.utils;

import io.mosip.certify.api.exception.DataProviderExchangeException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileReader;
import java.io.IOException; import java.util.*;

@Component
@Slf4j
public class CSVReader {
    private Map<String, List<Map<String, String>>> dataMap = new HashMap<>();

    //@Value("id")
    @Value("${mosip.certify.mock.data-provider.csv.identifier-column}")
    private String identifierColumn;
    @Value("${mosip.certify.mock.data-provider.csv.data-columns}")
    //@Value("id,fullName,dateOfBirth,employerName,employerAddress,employerCIN,gstNumber,employerContact,currentlyEmployed,authorizedDevices,photo,technicianID")
    private String includeFields;

    private Set<String> fieldsToInclude;

    @PostConstruct
    public void init() {
        // Convert comma-separated fields to Set
        // TODO: https://stackoverflow.com/questions/56454902/spring-value-with-arraylist-split-and-obtain-the-first-value
        // We can get all fields in the Set<String> directly
        fieldsToInclude = new HashSet<>(Arrays.asList(includeFields.split(",")));
    }

    public void readCSV(File f) throws IOException {
        try {
            // TODO: Eliminate nested try-catch
            try (FileReader reader = new FileReader(f);
                 CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
                // Get header names
                List<String> headers = csvParser.getHeaderNames();
                // Validate that identifier column exists
                if (!headers.contains(identifierColumn)) {
                    throw new IllegalArgumentException("Identifier column " + identifierColumn + " not found in CSV");
                }

                // Process each record
                for (CSVRecord record : csvParser) {
                    String identifier = record.get(identifierColumn);
                    Map<String, String> rowData = new HashMap<>();
                    // Store only the configured fields
                    for (String header : headers) {
                        if (fieldsToInclude.contains(header) || header.equals(identifierColumn)) {
                            rowData.put(header, record.get(header));
                        }
                    }

                    // Add to dataMap
                    dataMap.computeIfAbsent(identifier, k -> new ArrayList<>()).add(rowData);
                }
            } catch (IOException e) {
                log.error("Error finding csv file path", e);
                throw new IOException("Unable to find the CSV file.");
            }
        } catch (IOException e) {
            log.error("Error fetching csv file from classpath resource", e);
            throw new IOException("Unable to find the classpath resource for csv file.");
        }
    }

    public JSONObject getJsonObjectByIdentifier(String identifier) throws DataProviderExchangeException, JSONException {
        JSONObject jsonObject = new JSONObject();
        
        log.info("Current state of dataMap: {}", dataMap);

        // Log the input identifier
        log.info("Looking up data for identifier: {}", identifier);
    
        List<Map<String, String>> records = dataMap.get(identifier);
        
        if (records == null || records.isEmpty()) {
            // Log detailed error if no records are found
            log.error("No records found for identifier: {}", identifier);
            throw new DataProviderExchangeException("No record found in CSV with the provided identifier");
        }
    
        // Log the number of records found
        log.info("Found {} records for identifier: {}", records.size(), identifier);
    
        if (records != null && !records.isEmpty()) {
            Map<String, String> record = records.get(0);
    
            // Log the raw record data for debugging
            log.debug("Processing record: {}", record);
    
            // Add only configured fields to JSON object
            for (Map.Entry<String, String> entry : record.entrySet()) {
                if (fieldsToInclude.contains(entry.getKey()) || entry.getKey().equals(identifierColumn)) {
                    jsonObject.put(entry.getKey(), entry.getValue());
    
                    // Log each key-value pair added to the JSON object
                    log.debug("Added field to JSON: {} = {}", entry.getKey(), entry.getValue());
                }
            }
        }
    
        // Log the final JSON object before returning
        log.info("Generated JSON object for identifier {}: {}", identifier, jsonObject);
    
        return jsonObject;
    }
    
}
