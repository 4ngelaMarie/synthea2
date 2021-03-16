package org.mitre.synthea.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mitre.synthea.engine.Generator.GeneratorOptions;
import org.mitre.synthea.export.FhirR4;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.input.FixedRecord;
import org.mitre.synthea.input.FixedRecordGroup;
import org.mitre.synthea.world.agents.Payer;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.agents.Provider;
import org.mitre.synthea.world.concepts.HealthRecord;

public class FixedRecordTest {

  /* String Constants */
  public static final String BIRTH_YEAR = "birth_year";
  public static final String BIRTH_MONTH = "birth_month";
  public static final String BIRTH_DAY_OF_MONTH = "birth_day";
  public static final String FIRST_NAME = "first_name";
  public static final String LAST_NAME = "last_name";
  public static final String NAME = "full_name";
  public static final String GENDER = "gender";
  public static final String SEED_ID = "seed_id";
  public static final String RECORD_ID = "record_id";
  public static final String HH_ID = "hh_id";
  public static final String HH_STATUS = "hh_status";
  public static final String PHONE_CODE = "phone_code";
  public static final String PHONE_NUMBER = "phone_number";
  public static final String ADDRESS_SEQUENCE = "address_sequence";
  public static final String ADDRESS_1 = "address_1";
  public static final String ADDRESS_2 = "address_2";
  public static final String CITY = "city";
  public static final String STATE = "state";
  public static final String ZIP = "zip";
  public static final String ADDRESS_START = "address_start";
  public static final String CONTACT_FIRST_NAME = "contact_first";
  public static final String CONTACT_LAST_NAME = "contact_last";
  public static final String CONTACT_EMAIL = "email";

  /* Generator */
  private static Generator generator;

  /**
   * Configure settings to be set before tests.
   * 
   * @throws Exception on test configuration loading errors.
   */
  @BeforeClass
  public static void setup() {
    Generator.DEFAULT_STATE = Config.get("test_state.default", "California");
    Config.set("generate.only_dead_patients", "false");
    Config.set("exporter.split_records", "true");
    Config.set("generate.append_numbers_to_person_names", "false");
    Config.set("generate.only_alive_patients", "true");
    Provider.clear();
    Payer.clear();
    // Create a generator with the preset fixed demographics test file.
    GeneratorOptions go = new GeneratorOptions();
    go.fixedRecordPath = new File("./src/test/resources/fixed_demographics/households_fixed_demographics_test.json");
    go.state = "Colorado"; // Examples are based on California.
    go.population = 100; // Should be overwritten by number of patients in input file.
    go.overflow = false; // Prevent deceased patients from increasing the population size.
    // Prevent extraneous modules from being laoded to improve test speed.
    go.enabledModules = new ArrayList<String>();
    go.enabledModules.add("");
    generator = new Generator(go);
    generator.internalStore = new LinkedList<>(); // Allows us to access patients within generator.
    // List of raw RecordGroups imported directly from the input file for later
    // comparison.
    // fixedRecordGroupManager =
    // FixedRecordGroupManager.importFixedDemographicsFile(go.fixedRecordPath);
    // generator.fixedRecordGroupManager = fixedRecordGroupManager;
    // Generate each patient from the fixed record input file.
    // for (int i = 0; i < generator.options.population; i++) {
    // generator.generatePerson(i);
    // }
    generator.run();
  }

  /**
   * Resets any Configuration settings that may interfere with other tests.
   */
  @AfterClass
  public static void resetConfig() {
    Generator.DEFAULT_STATE = Config.get("test_state.default", "California");
    Config.set("exporter.split_records", "false");
    Config.set("generate.append_numbers_to_person_names", "true");
    Config.set("generate.only_alive_patients", "false");
  }

  @Test
  public void checkFixedDemographicsImport() {

    // Household with Lara Kayla Henderson and Chistopher Patrick Ahmann
    org.mitre.synthea.input.Household household = Generator.fixedRecordGroupManager.getHousehold("3879063");

    // Lara is this role and is the oldest member of the household, thus she should
    // have an instance of every seed record, fixed record group, and address
    // change.
    Person lara = household.getMember("single_1");

    FixedRecordGroup laraOnlyRecordGroup = Generator.fixedRecordGroupManager.getRecordGroupFor(lara);

    // Lara's final record group should be equivilant to the one in her attributes.
    assertEquals(laraOnlyRecordGroup, lara.attributes.get(Person.RECORD_GROUP));

    // Hard-coded checks for both of jane's record groups.
    Map<String, String> testAttributes = Stream.of(new String[][] { { RECORD_ID, "19001" }, { HH_ID, "3879063" },
        { HH_STATUS, "single_1" }, { FIRST_NAME, "Lara Kayla" }, { LAST_NAME, "Henderson" },
        { NAME, "Lara Kayla Henderson" }, { BIRTH_YEAR, "1980" }, { BIRTH_MONTH, "03" }, { BIRTH_DAY_OF_MONTH, "11" },
        { GENDER, "F" }, { PHONE_CODE, "303" }, { PHONE_NUMBER, "6377789" }, { ADDRESS_1, "11071 Lone Pnes" },
        { ADDRESS_2, "" }, { CITY, "Littleton" }, { STATE, "Colorado" }, { ZIP, "80125-9291" },
        { ADDRESS_SEQUENCE, "0" }, { CONTACT_EMAIL, "lkh1@something.com" } })
        .collect(Collectors.toMap(data -> data[0], data -> data[1]));
    testRecordAttributes(laraOnlyRecordGroup.seedRecord, testAttributes);
    // Check that the rest of the population's initial attributes match their seed
    // record.
    for (int i = 0; i < generator.options.population; i++) {
      FixedRecordGroup recordGroup = Generator.fixedRecordGroupManager.getNextRecordGroup(i);
      FixedRecord seedRecord = recordGroup.seedRecord;
      Map<String, Object> demoAttributes = generator.pickFixedDemographics(recordGroup, new Random(i));
      assertEquals("Seed Record in person and fixed record group do not match.", seedRecord,
          ((FixedRecordGroup) demoAttributes.get(Person.RECORD_GROUP)).seedRecord);
      assertEquals(demoAttributes.get(Person.FIRST_NAME), (seedRecord.firstName));
      assertEquals(demoAttributes.get(Person.LAST_NAME), (seedRecord.lastName));
      assertEquals(demoAttributes.get(Person.NAME), (seedRecord.firstName + " " + seedRecord.lastName));
      assertEquals(demoAttributes.get(Person.BIRTHDATE), (seedRecord.getBirthDate()));
      assertEquals(demoAttributes.get(Person.GENDER), (seedRecord.gender));
      assertEquals(demoAttributes.get(Person.TELECOM), (seedRecord.phoneAreaCode + "-" + seedRecord.phoneNumber));
      assertEquals(demoAttributes.get(Person.ADDRESS), (seedRecord.addressLineOne));
      assertEquals(demoAttributes.get(Person.STATE), (seedRecord.state));
      assertEquals(demoAttributes.get(Person.CITY), (seedRecord.city));
      assertEquals(demoAttributes.get(Person.ZIP), (seedRecord.zipcode));
    }
  }

  @Test
  public void checkFixedDemographicsExport() {
    // Check each patient from the fixed record input file.
    for (int personIndex = 0; personIndex < generator.options.population; personIndex++) {
      Person currentPerson = generator.internalStore.get(personIndex);
      // Check that each of the patients' exported FHIR resource attributes match a
      // variant record in the input file.
      for (HealthRecord healthRecord : currentPerson.records.values()) {

        // Convert the current record to exported FHIR.
        currentPerson.record = healthRecord;
        String fhirJson = FhirR4.convertToFHIRJson(currentPerson, System.currentTimeMillis());
        FhirContext ctx = FhirContext.forR4();
        IParser parser = ctx.newJsonParser().setPrettyPrint(true);
        Bundle bundle = parser.parseResource(Bundle.class, fhirJson);

        // Match the current record with the FixedRecord that matches its record id.
        FixedRecord currentFixedRecord = getRecordMatch(currentPerson);
        // Check that a record group was found. Very detailed error message for
        // debugging purposes.
        assertNotNull(
            "Did not find a record match for " + currentPerson.attributes.get(Person.NAME) + " with record id "
                + currentPerson.record.demographicsAtRecordCreation.get(Person.IDENTIFIER_RECORD_ID) + ". Person has "
                + ((FixedRecordGroup) currentPerson.attributes.get(Person.RECORD_GROUP)).variantRecords.size()
                + " imported fixed records in their fixed record group with record ids "
                + (((FixedRecordGroup) currentPerson.attributes.get(Person.RECORD_GROUP))).variantRecords.stream()
                    .map(variantRecord -> variantRecord.recordId).collect(Collectors.toList())
                + ". Person's health records have ids "
                + currentPerson.records.values().stream()
                    .map(record -> record.demographicsAtRecordCreation.get(Person.IDENTIFIER_RECORD_ID))
                    .collect(Collectors.toList()),
            currentFixedRecord);
        assertEquals(currentFixedRecord.recordId,
            healthRecord.demographicsAtRecordCreation.get(Person.IDENTIFIER_RECORD_ID));

        // First element of bundle is the patient resource.
        Patient patient = ((Patient) bundle.getEntry().get(0).getResource());
        // Birthdate parsing
        long millis = patient.getBirthDate().getTime();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        // Phone number parsing
        String[] phoneNumber = patient.getTelecomFirstRep().getValue().split("-");
        System.out.println("AHHH: " + patient.getNameFirstRep().getNameAsSingleString() + " X "
            + patient.getTelecomFirstRep().getValue() + " X "
            + patient.getIdentifier().get(patient.getIdentifier().size() - 3).getValue());
        String phoneCode = phoneNumber.length > 0 ? phoneNumber[0] : "";
        String phoneExtension = phoneNumber.length > 1 ? phoneNumber[1] : "";
        Map<String, String> testAttributes = new HashMap<String, String>();
        // = Stream.of(new String[][] {
        testAttributes.put(RECORD_ID, patient.getIdentifier().get(patient.getIdentifier().size() - 3).getValue());
        testAttributes.put(FIRST_NAME, patient.getNameFirstRep().getGivenAsSingleString());
        testAttributes.put(LAST_NAME, patient.getNameFirstRep().getFamily());
        testAttributes.put(NAME, patient.getNameFirstRep().getNameAsSingleString().replace("Mr. ", "")
            .replace("Ms. ", "").replace("Mrs. ", "").replace(" PhD", "").replace(" JD", "").replace(" MD", ""));
        testAttributes.put(BIRTH_YEAR, Integer.toString(c.get(Calendar.YEAR)));
        testAttributes.put(BIRTH_MONTH, Integer.toString(c.get(Calendar.MONTH) + 1));
        testAttributes.put(BIRTH_DAY_OF_MONTH, Integer.toString(c.get(Calendar.DAY_OF_MONTH)));
        testAttributes.put(GENDER, patient.getGender().getDisplay().substring(0, 1));
        testAttributes.put(PHONE_CODE, phoneCode);
        testAttributes.put(PHONE_NUMBER, phoneExtension);
        testAttributes.put(ADDRESS_1, patient.getAddressFirstRep().getLine().get(0).toString());
        testAttributes.put(CITY, patient.getAddressFirstRep().getCity());
        testAttributes.put(STATE, patient.getAddressFirstRep().getState());
        testAttributes.put(CONTACT_EMAIL, patient.getContact().get(0).getTelecom().get(0).getValue());
        testAttributes.put(ZIP, patient.getAddressFirstRep().getPostalCode());
        // Only Children have a contact person.
        if (patient.getContact().get(0).getName() != null) {
          testAttributes.put(CONTACT_LAST_NAME, patient.getContact().get(0).getName().getFamily());
          testAttributes.put(CONTACT_FIRST_NAME, patient.getContact().get(0).getName().getGivenAsSingleString());
        }
        assertNotNull("Person's variant records should have a seed id; seed records do not.",
            currentFixedRecord.seedID);
        testRecordAttributes(currentFixedRecord, testAttributes);
      }
    }
  }

  @Test
  public void checkFixedDemographicsVariantAttributes() {
    // Test that the correct number of people were imported from the fixed records
    // file.
    assertEquals(14, generator.internalStore.size());
    assertEquals(14, Generator.fixedRecordGroupManager.getPopulationSize());

    /* Test that the correct number of records were imported for each person. */
    // 0. Jane Doe
    Person janeDoe = generator.internalStore.get(0);
    FixedRecordGroup janeDoeRecordGroup = (FixedRecordGroup) janeDoe.attributes.get(Person.RECORD_GROUP);
    // Make sure the person has the correct number of variant fixed records.
    assertEquals(janeDoeRecordGroup.variantRecords.size(), 3);
    assertTrue(janeDoeRecordGroup.variantRecords.stream().anyMatch(record -> record.recordId.equals("101")));
    assertTrue(janeDoeRecordGroup.variantRecords.stream().anyMatch(record -> record.recordId.equals("102")));
    assertTrue(janeDoeRecordGroup.variantRecords.stream().anyMatch(record -> record.recordId.equals("103")));
    // Make sure the person has the correct number of health records.
    assertTrue("Records: " + janeDoe.records.size(), janeDoe.records.size() >= 3);
    assertTrue(janeDoe.records.values().stream()
        .anyMatch(record -> record.demographicsAtRecordCreation.get(Person.IDENTIFIER_RECORD_ID).equals("101")));
    assertTrue(janeDoe.records.values().stream()
        .anyMatch(record -> record.demographicsAtRecordCreation.get(Person.IDENTIFIER_RECORD_ID).equals("102")));
    assertTrue(janeDoe.records.values().stream()
        .anyMatch(record -> record.demographicsAtRecordCreation.get(Person.IDENTIFIER_RECORD_ID).equals("103")));
    // Test Jane Doe's VariantRecord 1.
    Map<String, String> testAttributes = Stream.of(new String[][] { { SEED_ID, "1" }, { RECORD_ID, "101" },
        { HH_ID, "1" }, { HH_STATUS, "adult" }, { FIRST_NAME, "Jane" }, { LAST_NAME, "Doe" }, { NAME, "Jane Doe" },
        { BIRTH_YEAR, "1974" }, { BIRTH_MONTH, "3" }, { BIRTH_DAY_OF_MONTH, "12" }, { GENDER, "F" },
        { PHONE_CODE, "405" }, { PHONE_NUMBER, "8762965" }, { ADDRESS_1, "56 Fetter Lane" }, { CITY, "San Francisco" },
        { STATE, "California" }, { ZIP, "34513" }, { ADDRESS_START, "1984" },
        { CONTACT_EMAIL, "jane-doe@something.com" } }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
    assertNotNull("Person's variant records should have a seed id; seed records do not.",
        janeDoeRecordGroup.variantRecords.get(0).seedID);
    testRecordAttributes(janeDoeRecordGroup.variantRecords.get(0), testAttributes);
    // Test Jane Doe's VariantRecord 2.
    testAttributes = Stream.of(new String[][] { { SEED_ID, "1" }, { RECORD_ID, "102" }, { HH_ID, "1" },
        { HH_STATUS, "adult" }, { FIRST_NAME, "Jane Janice" }, { LAST_NAME, "Dow" }, { NAME, "Jane Janice Dow" },
        { BIRTH_YEAR, "1984" }, { BIRTH_MONTH, "3" }, { BIRTH_DAY_OF_MONTH, "12" }, { GENDER, "F" },
        { PHONE_CODE, "405" }, { PHONE_NUMBER, "8762965" }, { ADDRESS_1, "fetter      lane" },
        { CITY, "San Francisco" }, { STATE, "California" }, { ZIP, "34513" }, { ADDRESS_START, "1999" },
        { CONTACT_EMAIL, "jane-doe@something.com" } }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
    assertNotNull("Person's variant records should have a seed id; seed records do not.",
        janeDoeRecordGroup.variantRecords.get(1).seedID);
    testRecordAttributes(janeDoeRecordGroup.variantRecords.get(1), testAttributes);
    // Test Jane Doe's VariantRecord 3.
    testAttributes = Stream.of(new String[][] { { SEED_ID, "1" }, { RECORD_ID, "103" }, { HH_ID, "1" },
        { HH_STATUS, "adult" }, { FIRST_NAME, "Jan" }, { LAST_NAME, "Doe" }, { NAME, "Jan Doe" },
        { BIRTH_YEAR, "1984" }, { BIRTH_MONTH, "3" }, { BIRTH_DAY_OF_MONTH, "12" }, { GENDER, "F" },
        { PHONE_CODE, "405" }, { PHONE_NUMBER, "1212121" }, { ADDRESS_1, "13 strawberry ln." },
        { CITY, "INVALID_CITY_NAME" }, { STATE, "California" }, { ZIP, "34513" }, { ADDRESS_START, "2002" },
        { CONTACT_EMAIL, "jane-doe@something.com" } }).collect(Collectors.toMap(data -> data[0], data -> data[1]));
    assertNotNull("Person's variant records should have a seed id; seed records do not.",
        janeDoeRecordGroup.variantRecords.get(2).seedID);
    testRecordAttributes(janeDoeRecordGroup.variantRecords.get(2), testAttributes);
    // 1. John Doe
    Person johnDoe = generator.internalStore.get(1);
    FixedRecordGroup johnDoeRecordGroup = (FixedRecordGroup) johnDoe.attributes.get(Person.RECORD_GROUP);
    // Make sure the person has the correct number of records.
    assertEquals(johnDoeRecordGroup.variantRecords.size(), 3);
    assertTrue("Records: " + johnDoe.records.size(), johnDoe.records.size() >= 3);
    // 2. Robert Doe
    Person robertDoe = generator.internalStore.get(2);
    FixedRecordGroup robertDoeRecordGroup = (FixedRecordGroup) robertDoe.attributes.get(Person.RECORD_GROUP);
    // Make sure the person has the correct number of records.
    assertEquals(robertDoeRecordGroup.variantRecords.size(), 2);
    assertTrue("Records: " + robertDoe.records.size(), robertDoe.records.size() >= 2);
    // 3. Sally Doe
    Person sallyDoe = generator.internalStore.get(3);
    FixedRecordGroup sallyDoeRecordGroup = (FixedRecordGroup) sallyDoe.attributes.get(Person.RECORD_GROUP);
    // Make sure the person has the correct number of records.
    assertEquals(sallyDoeRecordGroup.variantRecords.size(), 2);
    assertTrue("Records: " + sallyDoe.records.size(), sallyDoe.records.size() >= 2);
    // 4. Kate Smith
    Person kateSmith = generator.internalStore.get(4);
    FixedRecordGroup kateSmithRecordGroup = (FixedRecordGroup) kateSmith.attributes.get(Person.RECORD_GROUP);
    // Make sure the person has the correct number of records.
    assertEquals(kateSmithRecordGroup.variantRecords.size(), 2);
    assertTrue("Records: " + kateSmith.records.size(), kateSmith.records.size() >= 2);
    // 5. Frank Smith
    Person frankSmith = generator.internalStore.get(5);
    FixedRecordGroup frankSmithRecordGroup = (FixedRecordGroup) frankSmith.attributes.get(Person.RECORD_GROUP);
    // Make sure the person has the correct number of records.
    assertEquals(frankSmithRecordGroup.variantRecords.size(), 3);
    assertTrue("Records: " + frankSmith.records.size(), frankSmith.records.size() >= 3);
    // 6. William Smith
    Person williamSmith = generator.internalStore.get(6);
    FixedRecordGroup williamSmithRecordGroup = (FixedRecordGroup) williamSmith.attributes.get(Person.RECORD_GROUP);
    // Make sure the person has the correct number of records.
    assertEquals(williamSmithRecordGroup.variantRecords.size(), 1);
    assertTrue("Records: " + williamSmith.records.size(), williamSmith.records.size() >= 1);
  }

  private FixedRecord getRecordMatch(Person person) {
    FixedRecordGroup recordGroup = (FixedRecordGroup) person.attributes.get(Person.RECORD_GROUP);

    String currentRecordId = (String) person.record.demographicsAtRecordCreation.get(Person.IDENTIFIER_RECORD_ID);
    for (FixedRecord currentRecord : recordGroup.variantRecords) {
      if (currentRecord.recordId.equals(currentRecordId)) {
        return currentRecord;
      }
    }
    // If we reach here, there was no matching record id.
    return null;
  }

  @Test
  public void checkSeedHistory(){

    Person ritaNoble = Generator.fixedRecordGroupManager.getHousehold("59").getMember("married_1");

    // The variant records from the first time period should be based on the variants from the first fixed record group and seed record.

    // Pull all the exported health records.

    // Match each exported health record to a local variant record.

    // Make sure that there is at least one variant record for each of the fixed record groups (4 for rita noble).

    // Sort the variants and their paired exported records by date.

    // Make sure that each variant is from a correctly chronological fixed record group.


  }

  private void testRecordAttributes(FixedRecord fixedRecord, Map<String, String> testAttribtues) {
    assertEquals(fixedRecord.recordId, testAttribtues.get(RECORD_ID));
    assertEquals(
        "Expected: <" + fixedRecord.firstName + "> but was: <" + testAttribtues.get(FIRST_NAME)
            + ">. Fixed record id is: " + fixedRecord.recordId + ".",
        fixedRecord.firstName, testAttribtues.get(FIRST_NAME));
    assertEquals(fixedRecord.lastName, testAttribtues.get(LAST_NAME));
    assertEquals(fixedRecord.firstName + " " + fixedRecord.lastName, testAttribtues.get(NAME));
    assertEquals(fixedRecord.birthYear, testAttribtues.get(BIRTH_YEAR));
    assertTrue(fixedRecord.birthMonth.equals(testAttribtues.get(BIRTH_MONTH))
        || fixedRecord.birthMonth.equals("0" + testAttribtues.get(BIRTH_MONTH)));
    assertTrue(fixedRecord.birthDayOfMonth.equals(testAttribtues.get(BIRTH_DAY_OF_MONTH))
        || fixedRecord.birthDayOfMonth.equals("0" + testAttribtues.get(BIRTH_DAY_OF_MONTH)));
    assertEquals(fixedRecord.gender, testAttribtues.get(GENDER));
    assertEquals(fixedRecord.phoneAreaCode, testAttribtues.get(PHONE_CODE));
    assertEquals(fixedRecord.phoneNumber, testAttribtues.get(PHONE_NUMBER));
    assertEquals(fixedRecord.addressLineOne, testAttribtues.get(ADDRESS_1));
    // assertEquals(fixedRecord.addressLineTwo, testAttribtues.get(ADDRESS_2));
    assertEquals(fixedRecord.city, testAttribtues.get(CITY));
    assertEquals(fixedRecord.zipcode, testAttribtues.get(ZIP));
    // assertEquals(fixedRecord.contactEmail, testAttribtues.get(CONTACT_EMAIL));
    if (fixedRecord.contactFirstName != null) {
      // Only children have a contact person (in the fixed record test file).
      assertEquals(fixedRecord.contactFirstName, testAttribtues.get(CONTACT_FIRST_NAME));
      assertEquals(fixedRecord.contactLastName, testAttribtues.get(CONTACT_LAST_NAME));
    }
  }

  // @Test
  // public void checkAddressHistory() {
  // // Check that the correct address returns for the given years.
  // FixedRecordGroup frg = fixedRecordGroupManager.getNextRecordGroup(0);
  // // 1984 Address
  // frg.updateCurrentRecord(1984);
  // FixedRecord currentRecord = frg.getCurrentRecord();
  // assertEquals(currentRecord.addressLineOne, "56 Fetter Lane");
  // assertEquals(currentRecord.city, "San Francisco");
  // assertEquals(currentRecord.state, "California");
  // assertEquals(currentRecord.addressStartDate, 1984);
  // assertEquals(currentRecord.addressEndDate, 1998);
  // // 1999 Address
  // frg.updateCurrentRecord(1999);
  // currentRecord = frg.getCurrentRecord();
  // assertEquals(currentRecord.addressLineOne, "fetter lane");
  // assertEquals(currentRecord.city, "San Francisco");
  // assertEquals(currentRecord.state, "California");
  // assertEquals(currentRecord.addressStartDate, 1999);
  // assertEquals(currentRecord.addressEndDate, 2001);
  // // 2002 Address
  // frg.updateCurrentRecord(2002);
  // currentRecord = frg.getCurrentRecord();
  // assertEquals(currentRecord.addressLineOne, "13 strawberry ln.");
  // assertEquals(currentRecord.city, "INVALID_CITY_NAME");
  // assertEquals(currentRecord.state, "California");
  // assertEquals(currentRecord.addressStartDate, 2002);
  // // 1998 Address
  // frg.updateCurrentRecord(1998);
  // currentRecord = frg.getCurrentRecord();
  // assertEquals(currentRecord.addressLineOne, "56 Fetter Lane");
  // assertEquals(currentRecord.city, "San Francisco");
  // assertEquals(currentRecord.state, "California");
  // assertEquals(currentRecord.addressStartDate, 1984);
  // assertEquals(currentRecord.addressEndDate, 1998);
  // // 2001 Address
  // frg.updateCurrentRecord(2001);
  // currentRecord = frg.getCurrentRecord();
  // assertEquals(currentRecord.addressLineOne, "fetter lane");
  // assertEquals(currentRecord.city, "San Francisco");
  // assertEquals(currentRecord.state, "California");
  // assertEquals(currentRecord.addressStartDate, 1999);
  // assertEquals(currentRecord.addressEndDate, 2001);
  // // 2020 Address
  // frg.updateCurrentRecord(2020);
  // currentRecord = frg.getCurrentRecord();
  // assertEquals(currentRecord.addressLineOne, "13 strawberry ln.");
  // assertEquals(currentRecord.city, "INVALID_CITY_NAME");
  // assertEquals(currentRecord.state, "California");
  // assertEquals(currentRecord.addressStartDate, 2002);
  // }

  @Test
  public void checkHouseholds() {
    // Check that the households and their members were created properly.
    assertEquals(11, Generator.fixedRecordGroupManager.numberOfHouseholds());

    // Pull the 2 members of household 59.
    Person ritaNoble = Generator.fixedRecordGroupManager.getHousehold("59").getMember("married_1");
    Person justinNoble = Generator.fixedRecordGroupManager.getHousehold("59").getMember("married_2");

    // This only accounts for the final record group that each person has, not their first one.
    assertEquals(Generator.fixedRecordGroupManager.getRecordGroupFor(ritaNoble), ritaNoble.attributes.get(Person.RECORD_GROUP));
    assertEquals(Generator.fixedRecordGroupManager.getRecordGroupFor(justinNoble), justinNoble.attributes.get(Person.RECORD_GROUP));

    // Check that the record groups are the right people by checking the seed id.
    assertEquals("19489278", ((FixedRecordGroup) ritaNoble.attributes.get(Person.RECORD_GROUP)).getSeedId());
    assertEquals("19489279", ((FixedRecordGroup) justinNoble.attributes.get(Person.RECORD_GROUP)).getSeedId());
  }

  @Test
  public void variantRecordCityIsInvalid() {
    // // Set the current fixed record to the 2015 variant record of the first
    // person (Jane Doe).
    // fixedRecordGroupManager.getRecordGroup(0).updateCurrentRecord(1984);
    // assertTrue(fixedRecordGroupManager.getRecordGroup(0).updateCurrentRecord(2015));
    // // Jane Doe's 2015 variant record has an invalid city, so the safe city is
    // the seed city.
    // String validCity =
    // fixedRecordGroupManager.getRecordGroup(0).getSafeCurrentCity();
    // String invalidCity =
    // fixedRecordGroupManager.getRecordGroup(0).getCurrentRecord().city;
    // assertEquals("Eureka", validCity);
    // assertEquals("INVALID_CITY_NAME", invalidCity);
    // assertEquals(validCity,
    // fixedRecordGroupManager.getRecordGroup(0).getSeedCity());
    // assertEquals(validCity,
    // fixedRecordGroupManager.getRecordGroup(0).seedRecord.getCity());
    // // If a fixed record has an invalid city, getSafeCity should return null.
    // assertEquals(null,
    // fixedRecordGroupManager.getRecordGroup(0).getCurrentRecord().getCity());
  }

  @Test
  public void checkForcedProviderChange() {
    // List of prior providers that cannnot match a future one.
    List<String> providerIds = new ArrayList<String>();
    // Pull out Jane Doe to run provider tests on.
    Person person = Generator.fixedRecordGroupManager.getHousehold("59").getMember("married_1");
    // Pull out all existing Provider UUIDs.
    providerIds
        .addAll(person.records.values().stream().map(record -> record.provider.uuid).collect(Collectors.toList()));
    // Force a new provider.
    person.forceNewProvider(HealthRecord.EncounterType.WELLNESS, Utilities.getYear(0L));
    person.record = person.getHealthRecord(
        person.getProvider(HealthRecord.EncounterType.WELLNESS, System.currentTimeMillis()),
        System.currentTimeMillis());
    // Check that the new provider ID is not in the list of prior IDs.
    String firstUuid = person.record.provider.uuid;
    assertFalse(providerIds.stream().anyMatch(uuid -> uuid.equals(firstUuid)));
    // Force a new provider.
    person.forceNewProvider(HealthRecord.EncounterType.WELLNESS, Utilities.getYear(0L));
    person.record = person.getHealthRecord(
        person.getProvider(HealthRecord.EncounterType.WELLNESS, System.currentTimeMillis()),
        System.currentTimeMillis());
    // Check that the new provider ID is not in the list of prior IDs.
    String secondUuid = person.record.provider.uuid;
    assertFalse(providerIds.stream().anyMatch(uuid -> uuid.equals(secondUuid)));
    // Force a new provider.
    person.forceNewProvider(HealthRecord.EncounterType.WELLNESS, Utilities.getYear(0L));
    person.record = person.getHealthRecord(
        person.getProvider(HealthRecord.EncounterType.WELLNESS, System.currentTimeMillis()),
        System.currentTimeMillis());
    // Check that the new provider ID is not in the list of prior IDs.
    String thirdUuid = person.record.provider.uuid;
    assertFalse(providerIds.stream().anyMatch(uuid -> uuid.equals(thirdUuid)));
  }
}