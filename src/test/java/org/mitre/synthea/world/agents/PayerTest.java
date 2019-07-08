package org.mitre.synthea.world.agents;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.modules.HealthInsuranceModule;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;

import org.mitre.synthea.world.geography.Location;

public class PayerTest {

  Payer randomPrivatePayer;
  HealthInsuranceModule healthInsuranceModule;
  Person person;
  double povertyLevel;
  double medicaidLevel;
  long mandateTime;

  /**
   * Setup for Payer Tests.
   */
  @Before
  public void setup() {
    // Clear any Payers that may have already been statically loaded.
    Payer.clear();
    // Load in the .csv list of Payers for MA.
    Payer.loadPayers(new Location("Massachusetts", null));
    // Get the first Payer in the list for testing.
    randomPrivatePayer = Payer.getPrivatePayers().get(0);
    // Set up Medicaid/Medicare numbers.
    healthInsuranceModule = new HealthInsuranceModule();
    povertyLevel = Double
        .parseDouble(Config.get("generate.demographics.socioeconomic.income.poverty", "11000"));
    medicaidLevel = 1.33 * povertyLevel;
    // Set up Mandate year numbers.
    int mandateYear = Integer.parseInt(Config.get("generate.insurance.mandate.year", "2006"));
    mandateTime = Utilities.convertCalendarYearsToTime(mandateYear);
  }

  @Test
  public void incrementCustomersTest() {

    Person firstPerson = new Person(0L);
    firstPerson.attributes.put(Person.ID, UUID.randomUUID().toString());
    // Payer has firstPerson customer from the ages of 0 - 11.
    setPayerForYears(firstPerson, 0, 11);

    Person secondPerson = new Person(0L);
    secondPerson.attributes.put(Person.ID, UUID.randomUUID().toString());
    // Payer has secondPerson customer from the ages of 10 - 23.
    setPayerForYears(secondPerson, 10, 23);
    // Gap of coverage. Person is with Payer again from ages 55 - 60.
    setPayerForYears(secondPerson, 55, 60);

    // Ensure the first person was with the Payer for 12 years.
    assertEquals(12, randomPrivatePayer.getCustomerUtilization(firstPerson));
    // Ensure the second person was with the Payer for 20 years.
    assertEquals(20, randomPrivatePayer.getCustomerUtilization(secondPerson));
    // Ensure that there were 2 unique customers for the Payer.
    assertEquals(2, randomPrivatePayer.getUniqueCustomers());
  }

  /**
   * Sets the person's payer for the given year range.
   */
  private void setPayerForYears(Person person, int startAge, int endAge) {
    for (int i = startAge; i <= endAge; i++) {
      if (person.getPayerAtAge(i) == null) {
        person.setPayerAtAge(i, randomPrivatePayer);
        randomPrivatePayer.incrementCustomers(person);
      }
    }
  }

  @Test
  public void incrementEncountersTest() {

    person = new Person(0L);
    person.setPayerAtTime(0, randomPrivatePayer);
    HealthRecord healthRecord = new HealthRecord(person);

    healthRecord.encounterStart(0L, EncounterType.INPATIENT);
    healthRecord.encounterStart(0L, EncounterType.AMBULATORY);
    healthRecord.encounterStart(0L, EncounterType.EMERGENCY);

    assertEquals(3, randomPrivatePayer.getEncounterCount());
  }

  @Test
  public void recieveMedicareTests() {

    /* First Test: Older than 65 */
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    // Above Medicaid Income Level.
    person.attributes.put(Person.INCOME, (int) medicaidLevel * 100);
    // At time 2100000000000L, the person is 65 and qualifies for Medicare.
    healthInsuranceModule.process(person, 2100000000000L);
    assertEquals("Medicare", person.getPayerAtTime(2100000000000L).getName());

    /* Second Test: ESRD */
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.attributes.put(Person.GENDER, "M");
    person.attributes.put("end_stage_renal_disease", true);
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    // Above Medicaid Income Level.
    person.attributes.put(Person.INCOME, (int) medicaidLevel * 100);
    healthInsuranceModule.process(person, 0L);
    assertEquals("Medicare", person.getPayerAtTime(0L).getName());
  }

  @Test
  public void recieveMedicaidTests() {

    /* First Test: Pregnancy */
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put("pregnant", true);
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    // Above Medicaid Income Level.
    person.attributes.put(Person.INCOME, (int) medicaidLevel * 100);
    healthInsuranceModule.process(person, 0L);
    assertEquals("Medicaid", person.getPayerAtTime(0L).getName());

    /* Second Test: Poverty Level */
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    // Below Medicaid Income Level.
    person.attributes.put(Person.INCOME, (int) medicaidLevel - 1);
    healthInsuranceModule.process(person, 0L);
    assertEquals("Medicaid", person.getPayerAtTime(0L).getName());
  }

  @Test
  public void recieveDualEligibleTests() {

    /* Poverty Level and Over 65 */
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.attributes.put(Person.GENDER, "M");
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    // Below Medicaid Income Level.
    person.attributes.put(Person.INCOME, (int) medicaidLevel - 1);
    // At time 2100000000000L, the person is 65 and qualifies for Medicare.
    healthInsuranceModule.process(person, 2100000000000L);
    assertEquals("Dual Eligible", person.getPayerAtTime(2100000000000L).getName());
  }

  @Test
  public void recieveNoInsuranceTests() {

    /* First Test: Pre 2006 Mandate */
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, mandateTime - 10000);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.OCCUPATION_LEVEL, 0.1);
    // Barely above Medicaid Income Level.
    person.attributes.put(Person.INCOME, (int) medicaidLevel + 100);
    healthInsuranceModule.process(person, mandateTime - 10000);
    assertEquals("NO_INSURANCE", person.getPayerAtTime(0L).getName());
    
  }

  @Test
  public void recievePrivateInsuranceTests() {

    /* First Test: Post 2006 Mandate */
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, mandateTime + 10000);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    // Barely above Medicaid Income Level.
    person.attributes.put(Person.INCOME, (int) medicaidLevel + 100);
    healthInsuranceModule.process(person, mandateTime + 10000);
    assertNotEquals("NO_INSURANCE", person.getPayerAtTime(0L).getName());

    /* Second Test: Wealthy Enough to Purchase Private*/
    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, mandateTime - 10000);
    person.attributes.put(Person.GENDER, "F");
    person.attributes.put(Person.OCCUPATION_LEVEL, 1.0);
    // Above Medicaid Income Level.
    person.attributes.put(Person.INCOME, (int) medicaidLevel * 100);
    healthInsuranceModule.process(person, mandateTime - 10000);
    assertNotEquals("NO_INSURANCE", person.getPayerAtTime(0L).getName());
  }

  @Test
  public void loadGovernmentPayersTest() {
    assertTrue(Payer.getGovernmentPayer("Medicare")
        != null && Payer.getGovernmentPayer("Medicaid") != null);
  }

  @Test
  public void monthlyPremiumPaymentTest() {

    person = new Person(0L);
    person.attributes.put(Person.BIRTHDATE, 0L);
    person.attributes.put(Person.ID, UUID.randomUUID().toString());
    // Predetermine person's Payer.
    setPayerForYears(person, 0, 64);
    // Pay premium for 65 years.
    for (int year = 0; year <= 64; year++) {
      for (int month = 0; month < 24; month++) {
        // Person checks to pay twice a month. Only needs to pay once a month.
        healthInsuranceModule.process(person, Utilities.convertCalendarYearsToTime(year)
            + Utilities.convertTime("months", month/2));
      }
    }
    int totalMonthlyPremiumsOwed = (int) (randomPrivatePayer.getMonthlyPremium() * 12 * 65);
    assertEquals(totalMonthlyPremiumsOwed, randomPrivatePayer.getRevenue(), 0.1);
  }
}