package org.mitre.synthea.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mitre.synthea.engine.Generator.GeneratorOptions;
import org.mitre.synthea.export.FhirR4;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.input.FixedRecord;
import org.mitre.synthea.input.FixedRecordGroup;
import org.mitre.synthea.input.FixedRecordGroupManager;
import org.mitre.synthea.input.Household;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import ca.uhn.fhir.parser.IParser;

import ca.uhn.fhir.context.FhirContext;

public class FixedRecordTest {

  public static final String BIRTH_YEAR = "birth_year";
  public static final String BIRTH_MONTH = "birth_month";
  public static final String BIRTH_DAY_OF_MONTH = "birth_day";
  public static final String FIRST_NAME = "first_name";
  public static final String LAST_NAME = "last_name";
  public static final String GENDER = "gender";
  public static final String SEED_ID = "seed_id";
  public static final String RECORD_ID = "record_id";
  public static final String HH_ID = "hh_id";
  public static final String HH_STATUS = "hh_status";
  public static final String PHONE_CODE = "phone_code";
  public static final String PHONE_NUMBER = "phone_number";
  public static final String ADDRESS_1 = "address_1";
  public static final String ADDRESS_2 = "address_2";
  public static final String CITY = "city";
  public static final String STATE = "state";
  public static final String ZIP = "zip";
  public static final String ADDRESS_START = "address_start";
  public static final String CONTACT_FIRST_NAME = "contact_first";
  public static final String CONTACT_LAST_NAME = "contact_last";
  public static final String CONTACT_EMAIL = "email";

  // The generator.
  private static Generator generator;
  private static FixedRecordGroupManager fixedRecordGroupManager;

  // TODO: Do some closer-up tests of finding new providers when a record change occurs.
  // TODO: Test address movement.
  // TODO: Fix an issue where more than 2 adults might be added to a household.
  // TODO: Fix an issue where the wrong city may be displayed in the console.
  // TODO: Fix failing households tests.

  /**
   * Configure settings across these tests.
   * @throws Exception on test configuration loading errors.
   */
  @BeforeClass
  public static void setup() {
    Generator.DEFAULT_STATE = Config.get("test_state.default", "California");
    Config.set("generate.only_dead_patients", "false"); 
    Config.set("exporter.split_records", "true");
    Provider.clear();
    Payer.clear();
    // Create a generator with the preset fixed demographics test file.
    GeneratorOptions go = new GeneratorOptions();
    go.fixedRecordPath = new File(
        "src/test/resources/fixed_demographics/households_fixed_demographics_test.json");
    go.state = "California";  // Examples are based on California.
    go.population = 100;  // Should be overwritten by number of patients in input file.
    go.overflow = false;  // Prevent deceased patients from increasing the population size.
    go.enabledModules = new ArrayList<String>(); // Prevent extraneous modules from being laoded to improve test speed.
    go.enabledModules.add("");
    generator = new Generator(go);
    generator.internalStore = new LinkedList<>(); // Allows us to access patients within generator.
    // List of raw RecordGroups imported directly from the input file for later comparison.
    fixedRecordGroupManager = generator.importFixedDemographicsFile();   
  }

  @Test
  public void initialFixedDemographicsImportTest() {

    // First, we'll hard-code check the first person's (Jane Doe) seed record and initial attributes.
    FixedRecordGroup janeDoeRecordGroup = generator.fixedRecordGroupManager.getRecordGroup(0);
    Map<String, String> testAttributes = Stream.of(new String[][] {
      {RECORD_ID, "1"},
      {HH_ID, "1"},
      {HH_STATUS, "adult"},
      {FIRST_NAME, "Jane"},
      {LAST_NAME, "Doe"},
      {BIRTH_YEAR, "1984"},
      {BIRTH_MONTH, "3"},
      {BIRTH_DAY_OF_MONTH, "12"},
      {GENDER, "F"},
      {PHONE_CODE, "405"},
      {PHONE_NUMBER, "8762965"},
      {ADDRESS_1, "13 Strawberry Lane"},
      {CITY, "Eureka"},
      {STATE, "California"},
      {ZIP, "34513"},
      {CONTACT_EMAIL, "jane-doe@something.com"}
    }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
    testRecordAttributes(janeDoeRecordGroup.seedRecord, testAttributes);

    // Now check that the rest of the population's initial attributes match the seed record.
    for (int i = 0; i < generator.options.population; i++) {
      FixedRecordGroup recordGroup = generator.fixedRecordGroupManager.getRecordGroup(i);
      Map<String, Object> demoAttributes = generator.pickFixedDemographics(recordGroup, new Random(i));
      FixedRecord seedRecord = recordGroup.seedRecord;
      assertEquals(demoAttributes.get(Person.FIRST_NAME), (seedRecord.firstName));
      assertEquals(demoAttributes.get(Person.LAST_NAME), (seedRecord.lastName));
      assertEquals(demoAttributes.get(Person.ADDRESS), (seedRecord.addressLineOne));
      assertEquals(demoAttributes.get(Person.BIRTHDATE), (seedRecord.getBirthDate()));
      assertEquals(demoAttributes.get(Person.GENDER), (seedRecord.gender));
      assertEquals(demoAttributes.get(Person.TELECOM), (seedRecord.getTelecom()));
      assertEquals(demoAttributes.get(Person.STATE), (seedRecord.state));
      assertEquals(demoAttributes.get(Person.CITY), (seedRecord.city));
      assertEquals(demoAttributes.get(Person.ZIP), (seedRecord.zipcode));
    }
  }

  @Test
  public void fixedDemographicsTest() {

    // Generate each patient from the fixed record input file.
    for (int i = 0; i < generator.options.population; i++) {
      Person currentPerson = generator.generatePerson(i);
      // Cycle through the population and check that their exported FHIR resource attributes match their FixedRecords.
      for (String key : currentPerson.records.keySet()) {

        // Parse out the current record that we're checking.
        currentPerson.record = currentPerson.records.get(key);
        String fhirJson = FhirR4.convertToFHIRJson(currentPerson, System.currentTimeMillis());
        FhirContext ctx = FhirContext.forR4();
        IParser parser = ctx.newJsonParser().setPrettyPrint(true);
        Bundle bundle = parser.parseResource(Bundle.class, fhirJson);

        // Match the current record with the FixedRecord it should be equivalent to.
        FixedRecord currentFixedRecord = getRecordMatch(currentPerson, i);
        assertNotNull(currentFixedRecord);

        // first element of the bundle is the patient resource.
        Patient patient = ((Patient) bundle.getEntry().get(0).getResource());
        // Birthdate parsing
        long millis = patient.getBirthDate().getTime();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        // Phone number parsing
        String[] phoneNumber = patient.getTelecomFirstRep().getValue().split("-");
        Map<String, String> testAttributes = Stream.of(new String[][] {
          {FIRST_NAME, patient.getNameFirstRep().getGivenAsSingleString()},
          {LAST_NAME, patient.getNameFirstRep().getFamily()},
          {BIRTH_YEAR, Integer.toString(c.get(Calendar.YEAR))},
          {BIRTH_MONTH, Integer.toString(c.get(Calendar.MONTH) + 1)},
          {BIRTH_DAY_OF_MONTH, Integer.toString(c.get(Calendar.DAY_OF_MONTH))},
          {GENDER, patient.getGender().getDisplay().substring(0,1)},
          {PHONE_CODE, phoneNumber[0]},
          {PHONE_NUMBER, phoneNumber[1]},
          {ADDRESS_1, patient.getAddressFirstRep().getLine().get(0).toString()},
          {CITY, patient.getAddressFirstRep().getCity()},
          {STATE, patient.getAddressFirstRep().getState()},
          {CONTACT_EMAIL, patient.getContact().get(0).getTelecom().get(0).getValue()},
          {ZIP, patient.getAddressFirstRep().getPostalCode()},
        }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
        // Only Children have a contact person.
        if(patient.getContact().get(0).getName() != null) {
          testAttributes.put(CONTACT_LAST_NAME, patient.getContact().get(0).getName().getFamily());
          testAttributes.put(CONTACT_FIRST_NAME, patient.getContact().get(0).getName().getGivenAsSingleString());
        }
        testRecordAttributes(currentFixedRecord, testAttributes);
      }
    }
    
    // Test that the correct number of people were imported from the fixed records file.
    assertEquals(7, generator.internalStore.size());
    assertEquals(7, fixedRecordGroupManager.getPopulationSize());

    /* Test that the correct number of records were imported for each person. */
    // 0. Jane Doe
    Person janeDoe = generator.internalStore.get(0);
    FixedRecordGroup janeDoeRecordGroup
        = (FixedRecordGroup) janeDoe.attributes.get(Person.RECORD_GROUP);
    // Make sure the person has the correct number of variant fixed records.
    assertEquals(janeDoeRecordGroup.variantRecords.size(), 3);
    assertTrue(janeDoeRecordGroup.variantRecords.stream().anyMatch(record -> record.recordId.equals("101")));
    assertTrue(janeDoeRecordGroup.variantRecords.stream().anyMatch(record -> record.recordId.equals("102")));
    assertTrue(janeDoeRecordGroup.variantRecords.stream().anyMatch(record -> record.recordId.equals("103")));
    // Make sure the person has the correct number of health records.
    assertTrue("Records: " + janeDoe.records.size(), janeDoe.records.size() >= 3);
    assertTrue(janeDoe.records.values().stream().anyMatch(record -> record.demographicsAtRecordCreation.get(Person.IDENTIFIER_RECORD_ID).equals("101")));
    assertTrue(janeDoe.records.values().stream().anyMatch(record -> record.demographicsAtRecordCreation.get(Person.IDENTIFIER_RECORD_ID).equals("102")));
    assertTrue(janeDoe.records.values().stream().anyMatch(record -> record.demographicsAtRecordCreation.get(Person.IDENTIFIER_RECORD_ID).equals("103")));
    // Test Jane Doe's VariantRecord 1.
    Map<String, String> testAttributes = Stream.of(new String[][] {
      {SEED_ID, "1"},
      {RECORD_ID, "101"},
      {HH_ID, "1"},
      {HH_STATUS, "adult"},
      {FIRST_NAME, "Jane"},
      {LAST_NAME, "Doe"},
      {BIRTH_YEAR, "1974"},
      {BIRTH_MONTH, "3"},
      {BIRTH_DAY_OF_MONTH, "12"},
      {GENDER, "F"},
      {PHONE_CODE, "405"},
      {PHONE_NUMBER, "8762965"},
      {ADDRESS_1, "56 Fetter Lane"},
      {CITY, "San Francisco"},
      {STATE, "California"},
      {ZIP, "34513"},
      {ADDRESS_START, "1984"},
      {CONTACT_EMAIL, "jane-doe@something.com"}
    }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
    testRecordAttributes(janeDoeRecordGroup.variantRecords.get(0), testAttributes);
    // Test Jane Doe's VariantRecord 2.
    testAttributes = Stream.of(new String[][] {
      {SEED_ID, "1"},
      {RECORD_ID, "102"},
      {HH_ID, "1"},
      {HH_STATUS, "adult"},
      {FIRST_NAME, "Jane Janice"},
      {LAST_NAME, "Dow"},
      {BIRTH_YEAR, "1984"},
      {BIRTH_MONTH, "3"},
      {BIRTH_DAY_OF_MONTH, "12"},
      {GENDER, "F"},
      {PHONE_CODE, "405"},
      {PHONE_NUMBER, "8762965"},
      {ADDRESS_1, "fetter      lane"},
      {CITY, "San Francisco"},
      {STATE, "California"},
      {ZIP, "34513"},
      {ADDRESS_START, "1999"},
      {CONTACT_EMAIL, "jane-doe@something.com"}
    }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
    testRecordAttributes(janeDoeRecordGroup.variantRecords.get(1), testAttributes);
    // Test Jane Doe's VariantRecord 3.
    testAttributes = Stream.of(new String[][] {
      {SEED_ID, "1"},
      {RECORD_ID, "103"},
      {HH_ID, "1"},
      {HH_STATUS, "adult"},
      {FIRST_NAME, "Jan"},
      {LAST_NAME, "Doe"},
      {BIRTH_YEAR, "1984"},
      {BIRTH_MONTH, "3"},
      {BIRTH_DAY_OF_MONTH, "12"},
      {GENDER, "F"},
      {PHONE_CODE, "405"},
      {PHONE_NUMBER, "1212121"},
      {ADDRESS_1, "13 strawberry ln."},
      {CITY, "INVALID_CITY_NAME"},
      {STATE, "California"},
      {ZIP, "34513"},
      {ADDRESS_START, "2002"},
      {CONTACT_EMAIL, "jane-doe@something.com"}
    }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
    testRecordAttributes(janeDoeRecordGroup.variantRecords.get(2), testAttributes);
    // 1. John Doe
    Person johnDoe = generator.internalStore.get(1);
    FixedRecordGroup johnDoeRecordGroup
        = (FixedRecordGroup) johnDoe.attributes.get(Person.RECORD_GROUP);
    // Make sure the person has the correct number of records.
    assertEquals(johnDoeRecordGroup.variantRecords.size(), 3);
    assertTrue("Records: " + johnDoe.records.size(), johnDoe.records.size() >= 3);
    // 2. Robert Doe
    Person robertDoe = generator.internalStore.get(2);
    FixedRecordGroup robertDoeRecordGroup
        = (FixedRecordGroup) robertDoe.attributes.get(Person.RECORD_GROUP);
    // Make sure the person has the correct number of records.
    assertEquals(robertDoeRecordGroup.variantRecords.size(), 2);
    assertTrue("Records: " + robertDoe.records.size(), robertDoe.records.size() >= 2);
    // 3. Sally Doe
    Person sallyDoe = generator.internalStore.get(3);
    FixedRecordGroup sallyDoeRecordGroup
        = (FixedRecordGroup) sallyDoe.attributes.get(Person.RECORD_GROUP);
    // Make sure the person has the correct number of records.
    assertEquals(sallyDoeRecordGroup.variantRecords.size(), 2);
    assertTrue("Records: " + sallyDoe.records.size(), sallyDoe.records.size() >= 2);
    // 4. Kate Smith
    Person kateSmith = generator.internalStore.get(4);
    FixedRecordGroup kateSmithRecordGroup
        = (FixedRecordGroup) kateSmith.attributes.get(Person.RECORD_GROUP);
    // Make sure the person has the correct number of records.
    assertEquals(kateSmithRecordGroup.variantRecords.size(), 2);
    assertTrue("Records: " + kateSmith.records.size(), kateSmith.records.size() >= 2);
    // 5. Frank Smith
    Person frankSmith = generator.internalStore.get(5);
    FixedRecordGroup frankSmithRecordGroup
        = (FixedRecordGroup) frankSmith.attributes.get(Person.RECORD_GROUP);
    // Make sure the person has the correct number of records.
    assertEquals(frankSmithRecordGroup.variantRecords.size(), 3);
    assertTrue("Records: " + frankSmith.records.size(), frankSmith.records.size() >= 3);
    // 6. William Smith
    Person williamSmith = generator.internalStore.get(6);
    FixedRecordGroup williamSmithRecordGroup
        = (FixedRecordGroup) williamSmith.attributes.get(Person.RECORD_GROUP);
    // Make sure the person has the correct number of records.
    assertEquals(williamSmithRecordGroup.variantRecords.size(), 1);
    assertTrue("Records: " + williamSmith.records.size(), williamSmith.records.size() >= 1);
  }

  private FixedRecord getRecordMatch(Person person, int index) {
    FixedRecordGroup recordGroup = fixedRecordGroupManager.getRecordGroup(index);

    String recordId = (String) person.record.demographicsAtRecordCreation.get(Person.IDENTIFIER_RECORD_ID);
    System.out.println("record id " + recordId);
    for(FixedRecord record : recordGroup.variantRecords) {
      if(record.recordId.equals(recordId)){
        return record;
      }
    }
    // If we reach here, there was no matching record id.
    return null;
  }

  public void testRecordAttributes(FixedRecord personFixedRecord, Map<String, String> testAttribtues) {
    assertEquals(personFixedRecord.firstName, testAttribtues.get(FIRST_NAME));
    assertEquals(personFixedRecord.lastName, testAttribtues.get(LAST_NAME));
    assertEquals(personFixedRecord.birthYear, testAttribtues.get(BIRTH_YEAR));
    assertEquals(personFixedRecord.birthMonth, testAttribtues.get(BIRTH_MONTH));
    assertEquals(personFixedRecord.birthDayOfMonth, testAttribtues.get(BIRTH_DAY_OF_MONTH));
    assertEquals(personFixedRecord.gender, testAttribtues.get(GENDER));
    assertEquals(personFixedRecord.phoneAreaCode, testAttribtues.get(PHONE_CODE));
    assertEquals(personFixedRecord.phoneNumber, testAttribtues.get(PHONE_NUMBER));
    assertEquals(personFixedRecord.addressLineOne, testAttribtues.get(ADDRESS_1));
    assertEquals(personFixedRecord.addressLineTwo, testAttribtues.get(ADDRESS_2));
    assertEquals(personFixedRecord.city, testAttribtues.get(CITY));
    assertEquals(personFixedRecord.zipcode, testAttribtues.get(ZIP));
    assertEquals(personFixedRecord.contactEmail, testAttribtues.get(CONTACT_EMAIL));
    if(personFixedRecord.contactFirstName != null){
      // Only children have a contact person.
      assertEquals(personFixedRecord.contactFirstName, testAttribtues.get(CONTACT_FIRST_NAME));
      assertEquals(personFixedRecord.contactLastName, testAttribtues.get(CONTACT_LAST_NAME));
    }
  }

  @Test
  public void variantRecordCityIsInvalid() {
    // First, set the current fixed record to the 2015 variant record of the first person (Jane Doe).
    fixedRecordGroupManager.getRecordGroup(0).updateCurrentRecord(1984);
    assertTrue(fixedRecordGroupManager.getRecordGroup(0).updateCurrentRecord(2015));
    // Jane Doe's 2015 variant record has an invalid city, so the safe city is the seed city.
    String validCity = fixedRecordGroupManager.getRecordGroup(0).getSafeCurrentCity();
    String invalidCity = fixedRecordGroupManager.getRecordGroup(0).getCurrentRecord().city;
    assertEquals("Eureka", validCity);
    assertEquals("INVALID_CITY_NAME", invalidCity);
    assertEquals(validCity, fixedRecordGroupManager.getRecordGroup(0).getSeedCity());
    assertEquals(validCity, fixedRecordGroupManager.getRecordGroup(0).seedRecord.getSafeCity());
    // If a fixed record has an invalid city, getSafeCity should return null.
    assertEquals(null, fixedRecordGroupManager.getRecordGroup(0).getCurrentRecord().getSafeCity());
  }

  @Test
  public void checkHouseholdsTest() {
    // Make sure that the correct number of households were generated.
    assertEquals(2, generator.households.size());
    Map<Integer, Household> households = generator.households;
    // Household 1 should have the following members:
    assertTrue(households.get(1).getAdults().stream().anyMatch(adult -> ((FixedRecordGroup) adult.attributes.get(Person.RECORD_GROUP)).seedRecord.recordId.equals("1")));
    assertTrue(households.get(1).getAdults().stream().anyMatch(adult -> ((FixedRecordGroup) adult.attributes.get(Person.RECORD_GROUP)).seedRecord.recordId.equals("2")));
    assertTrue(households.get(1).getAdults().size() == 2);
    assertTrue(households.get(1).getDependents().stream().anyMatch(dependent -> ((FixedRecordGroup) dependent.attributes.get(Person.RECORD_GROUP)).seedRecord.recordId.equals("5")));
    assertTrue(households.get(1).getDependents().stream().anyMatch(dependent -> ((FixedRecordGroup) dependent.attributes.get(Person.RECORD_GROUP)).seedRecord.recordId.equals("6")));
    assertTrue(households.get(1).getDependents().size() == 2);
    // Household 2 should have the following members:
    assertTrue(households.get(2).getAdults().stream().anyMatch(adult -> ((FixedRecordGroup) adult.attributes.get(Person.RECORD_GROUP)).seedRecord.recordId.equals("3")));
    assertTrue(households.get(2).getAdults().stream().anyMatch(adult -> ((FixedRecordGroup) adult.attributes.get(Person.RECORD_GROUP)).seedRecord.recordId.equals("4")));
    assertTrue(households.get(2).getAdults().size() == 2);
    assertTrue(households.get(2).getDependents().stream().anyMatch(dependent -> ((FixedRecordGroup) dependent.attributes.get(Person.RECORD_GROUP)).seedRecord.recordId.equals("7")));
    assertTrue(households.get(2).getDependents().size() == 1);
  }

  @Test(expected = RuntimeException.class)
  public void moreThanTwoAdultsInHouseholdTest() {
    Household houshold = new Household(0);
    houshold.addAdult(new Person(0));
    houshold.addAdult(new Person(0));
    // On the third adult, a Runtime Exception should be called since there can only be 2 adults.
    houshold.addAdult(new Person(0));
  }
}