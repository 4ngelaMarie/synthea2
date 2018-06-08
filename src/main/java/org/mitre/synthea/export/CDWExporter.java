package org.mitre.synthea.export;

import static org.mitre.synthea.export.ExportHelper.dateFromTimestamp;
import static org.mitre.synthea.export.ExportHelper.iso8601Timestamp;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.sis.geometry.DirectPosition2D;
import org.mitre.synthea.engine.Event;
import org.mitre.synthea.helpers.FactTable;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.Costs;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.CarePlan;
import org.mitre.synthea.world.concepts.HealthRecord.Code;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;
import org.mitre.synthea.world.concepts.HealthRecord.Entry;
import org.mitre.synthea.world.concepts.HealthRecord.ImagingStudy;
import org.mitre.synthea.world.concepts.HealthRecord.Medication;
import org.mitre.synthea.world.concepts.HealthRecord.Observation;
import org.mitre.synthea.world.concepts.HealthRecord.Procedure;
import org.mitre.synthea.world.geography.Location;

/**
 * This exporter attempts to export synthetic patient data into 
 * comma-separated value (CSV) files that align with the Veteran's 
 * Health Administration (VHA) Corporate Data Warehouse (CDW).
 * <p/>
 * https://www.data.va.gov/dataset/corporate-data-warehouse-cdw
 */
public class CDWExporter {
  /**
   * Table key sequence generators.
   */
  private Map<FileWriter,AtomicInteger> sids;
  
  private FactTable maritalStatus = new FactTable();
  private FactTable sta3n = new FactTable();
  private FactTable location = new FactTable();
  // private FactTable appointmentStatus = new FactTable();
  // private FactTable appointmentType = new FactTable();
  
  /**
   * Writers for patient data.
   */
  private FileWriter spatient;
  private FileWriter spatientaddress;
  private FileWriter spatientphone;
  private FileWriter patientrace;
  private FileWriter patientethnicity;

  /**
   * Writers for encounter data.
   */
  private FileWriter consult;
  private FileWriter visit;
  private FileWriter appointment;
  private FileWriter inpatient;

  /**
   * System-dependent string for a line break. (\n on Mac, *nix, \r\n on Windows)
   */
  private static final String NEWLINE = System.lineSeparator();

  /**
   * Constructor for the CDWExporter -
   *  initialize the required files and associated writers.
   */
  private CDWExporter() {
    sids = new HashMap<FileWriter,AtomicInteger>();
    
    try {
      File output = Exporter.getOutputFolder("cdw", null);
      output.mkdirs();
      Path outputDirectory = output.toPath();

      // Patient Data
      spatient = openFileWriter(outputDirectory, "spatient.csv");
      spatientaddress = openFileWriter(outputDirectory, "spatientaddress.csv");
      spatientphone = openFileWriter(outputDirectory, "spatientphone.csv");
      patientrace = openFileWriter(outputDirectory, "patientrace.csv");
      patientethnicity = openFileWriter(outputDirectory, "patientethnicity.csv");

      // Encounter Data
      consult = openFileWriter(outputDirectory, "consult.csv");
      visit = openFileWriter(outputDirectory, "visit.csv");
      appointment = openFileWriter(outputDirectory, "appointment.csv");
      inpatient = openFileWriter(outputDirectory, "inpatient.csv");

      writeCSVHeaders();
    } catch (IOException e) {
      // wrap the exception in a runtime exception.
      // the singleton pattern below doesn't work if the constructor can throw
      // and if these do throw ioexceptions there's nothing we can do anyway
      throw new RuntimeException(e);
    }
  }

  private FileWriter openFileWriter(Path outputDirectory, String filename) throws IOException {
    File file = outputDirectory.resolve(filename).toFile();
    return new FileWriter(file);
  }

  /**
   * Write the headers to each of the CSV files.
   * @throws IOException if any IO error occurs
   */
  private void writeCSVHeaders() throws IOException {
    // Fact Tables
    maritalStatus.setHeader("MaritalStatusSID,MaritalStatusCode");
    sta3n.setHeader("Sta3n,Sta3nName,TimeZone");
    location.setHeader("LocationSID,LocationName");

    // Patient Tables
    spatient.write("PatientSID,PatientName,PatientLastName,PatientFirstName,PatientSSN,Age,"
        + "BirthDateTime,DeceasedFlag,DeathDateTime,Gender,SelfIdentifiedGender,Religion,"
        + "MaritalStatus,MaritalStatusSID,PatientEnteredDateTime");
    spatient.write(NEWLINE);
    spatientaddress.write("SPatientAddressSID,PatientSID,AddressType,NameOfContact,"
        + "RelationshipToPatient,StreetAddress1,StreetAddress2,StreetAddress3,"
        + "City,State,Zip,Country,GISMatchScore,GISStreetSide,"
        + "GISPatientAddressLongitude,GISPatientAddressLatitude,GISFIPSCode");
    spatientaddress.write(NEWLINE);
    spatientphone.write("SPatientPhoneSID,PatientSID,PatientContactType,NameOfContact,"
        + "RelationshipToPatient,PhoneNumber,WorkPhoneNumber,EmailAddress");
    spatientphone.write(NEWLINE);
    patientrace.write("PatientRaceSID,PatientSID,Race");
    patientrace.write(NEWLINE);
    patientethnicity.write("PatientEthnicitySID,PatientSID,Ethnicity");
    patientethnicity.write(NEWLINE);

    // Encounter Tables
    consult.write("ConsultSID,ToRequestServiceSID");
    consult.write(NEWLINE);
    visit.write("VisitSID,VisitDateTime,CreatedByStaffSID,LocationSID,PatientSID");
    visit.write(NEWLINE);
    appointment.write("AppointmentSID,Sta3n,PatientSID,AppointmentDateTime,AppointmentMadeDate,"
        + "AppointmentTypeSID,AppointmentStatus,VisitSID,LocationSID,PurposeOfVisit,"
        + "SchedulingRequestType,FollowUpVisitFlag,LengthOfAppointment,ConsultSID,"
        + "CheckInDateTime,CheckOutDateTime");
    appointment.write(NEWLINE);
    inpatient.write("InpatientSID,PatientSID,AdmitDateTime");
    inpatient.write(NEWLINE);
  }

  /**
   *  Thread safe singleton pattern adopted from
   *  https://stackoverflow.com/questions/7048198/thread-safe-singletons-in-java
   */
  private static class SingletonHolder {
    /**
     * Singleton instance of the CDWExporter.
     */
    private static final CDWExporter instance = new CDWExporter();
  }

  /**
   * Get the current instance of the CDWExporter.
   * @return the current instance of the CDWExporter.
   */
  public static CDWExporter getInstance() {
    return SingletonHolder.instance;
  }

  /**
   * Add a single Person's health record info to the CSV records.
   * @param person Person to write record data for
   * @param time Time the simulation ended
   * @throws IOException if any IO error occurs
   */
  public void export(Person person, long time) throws IOException {
    // TODO Ignore civilians, only consider the veteran population.
    //    if (!person.attributes.containsKey("veteran")) {
    //      return;
    //    }
    
    int personID = patient(person, time);

    for (Encounter encounter : person.record.encounters) {
      int encounterID = encounter(personID, person, encounter);

      for (HealthRecord.Entry condition : encounter.conditions) {
        condition(personID, encounterID, condition);
      }

      for (HealthRecord.Entry allergy : encounter.allergies) {
        allergy(personID, encounterID, allergy);
      }

      for (Observation observation : encounter.observations) {
        observation(personID, encounterID, observation);
      }

      for (Procedure procedure : encounter.procedures) {
        procedure(personID, encounterID, procedure);
      }

      for (Medication medication : encounter.medications) {
        medication(personID, encounterID, medication);
      }

      for (HealthRecord.Entry immunization : encounter.immunizations) {
        immunization(personID, encounterID, immunization);
      }

      for (CarePlan careplan : encounter.careplans) {
        careplan(personID, encounterID, careplan);
      }

      for (ImagingStudy imagingStudy : encounter.imagingStudies) {
        imagingStudy(personID, encounterID, imagingStudy);
      }
    }

    // Patient Data
    spatient.flush();
    spatientaddress.flush();
    spatientphone.flush();
    patientrace.flush();
    patientethnicity.flush();

    // Encounter Data
    consult.flush();
    visit.flush();
    appointment.flush();
    inpatient.flush();
  }
  
  /**
   * Fact Tables should only be written after all patients have completed export.
   */
  public void writeFactTables() {
    try {
      File output = Exporter.getOutputFolder("cdw", null);
      output.mkdirs();
      Path outputDirectory = output.toPath();
      maritalStatus.write(openFileWriter(outputDirectory,"maritalstatus.csv"));
      sta3n.write(openFileWriter(outputDirectory,"sta3n.csv"));
      location.write(openFileWriter(outputDirectory,"location.csv"));
    } catch (IOException e) {
      // wrap the exception in a runtime exception.
      // the singleton pattern below doesn't work if the constructor can throw
      // and if these do throw ioexceptions there's nothing we can do anyway
      throw new RuntimeException(e);
    }
  }

  /**
   * Record a Patient.
   *
   * @param person Person to write data for
   * @param time Time the simulation ended, to calculate age/deceased status
   * @return the patient's ID, to be referenced as a "foreign key" if necessary
   * @throws IOException if any IO error occurs
   */
  private int patient(Person person, long time) throws IOException {
    //    spatient.write("PatientSID,PatientName,PatientLastName,PatientFirstName,PatientSSN,Age,"
    //        + "BirthDateTime,DeceasedFlag,DeathDateTime,Gender,SelfIdentifiedGender,Religion,"
    //        + "MaritalStatus,MaritalStatusSID,PatientEnteredDateTime");    
    StringBuilder s = new StringBuilder();
    int personID = getNextKey(spatient);
    s.append(personID).append(',');
    
    if (person.attributes.containsKey(Person.NAME_PREFIX)) {
      s.append(person.attributes.get(Person.NAME_PREFIX)).append(' ');
    }
    s.append(person.attributes.get(Person.FIRST_NAME)).append(' ');
    s.append(person.attributes.get(Person.LAST_NAME));
    if (person.attributes.containsKey(Person.NAME_SUFFIX)) {
      s.append(' ').append(person.attributes.get(Person.NAME_SUFFIX));
    }
    s.append(',').append(clean((String) person.attributes.getOrDefault(Person.LAST_NAME, "")));
    s.append(',').append(clean((String) person.attributes.getOrDefault(Person.FIRST_NAME, "")));
    s.append(',').append(clean((String) person.attributes.getOrDefault(Person.IDENTIFIER_SSN, "")));

    boolean alive = person.alive(time);
    int age = 0;
    if (alive) {
      age = person.ageInYears(time);
    } else {
      age = person.ageInYears(person.events.event(Event.DEATH).time);
    }
    s.append(',').append(age);
    s.append(',').append(iso8601Timestamp((long) person.attributes.get(Person.BIRTHDATE)));

    if (alive) {
      s.append(',').append('N').append(',');
    } else {
      s.append(',').append('Y');
      s.append(',').append(iso8601Timestamp(person.events.event(Event.DEATH).time));
    }
    if (person.attributes.get(Person.GENDER).equals("M")) {
      s.append(",M,Male");
    } else {
      s.append(",F,Female");      
    }
    
    s.append(",None"); // Religion
    
    // Currently there are no divorces or widows
    // Legal codes: (D)ivorced, (N)ever Married, (S)eperated, (W)idowed, (M)arried, (U)nknown
    String marital = ((String) person.attributes.get(Person.MARITAL_STATUS));
    if (marital != null) {
      if (marital.equals("M")) {
        s.append(",Married");
      } else {
        marital = "N";
        s.append(",Never Married");
      }
    } else {
      marital = "U";
      s.append(",Unknown");
    }
    s.append(',').append(maritalStatus.addFact(marital, marital));
    
    // TODO Need an enlistment date or date they became a veteran.
    s.append(',').append(iso8601Timestamp(time - Utilities.convertTime("years", 10)));
    s.append(NEWLINE);
    write(s.toString(), spatient);
    
    //  spatientaddress.write("SPatientAddressSID,PatientSID,AddressType,NameOfContact,"
    //  + "RelationshipToPatient,StreetAddress1,StreetAddress2,StreetAddress3,"
    //  + "City,State,Zip,Country,GISMatchScore,GISStreetSide,"
    //  + "GISPatientAddressLongitude,GISPatientAddressLatitude,GISFIPSCode");
    s.setLength(0);
    s.append(getNextKey(spatientaddress)).append(',');
    s.append(personID).append(',');
    s.append("Legal Residence").append(',');
    s.append(person.attributes.get(Person.FIRST_NAME)).append(' ');
    s.append(person.attributes.get(Person.LAST_NAME)).append(',');
    s.append("Self").append(',');
    s.append(person.attributes.get(Person.ADDRESS)).append(",,,");
    s.append(person.attributes.get(Person.CITY)).append(',');
    s.append(person.attributes.get(Person.STATE)).append(',');
    s.append(person.attributes.get(Person.ZIP)).append(",USA,,,");
    
    DirectPosition2D coord = (DirectPosition2D) person.attributes.get(Person.COORDINATE);
    if (coord != null) {
      s.append(coord.x).append(',').append(coord.y).append(',');
    } else {
      s.append(",,");
    }
    s.append(NEWLINE);
    write(s.toString(), spatientaddress);
    
    //spatientphone.write("SPatientPhoneSID,PatientSID,PatientContactType,NameOfContact,"
    //  + "RelationshipToPatient,PhoneNumber,WorkPhoneNumber,EmailAddress");
    s.setLength(0);
    s.append(getNextKey(spatientphone)).append(',');
    s.append(personID).append(',');
    s.append("Patient Cell Phone").append(',');
    s.append(person.attributes.get(Person.FIRST_NAME)).append(' ');
    s.append(person.attributes.get(Person.LAST_NAME)).append(',');
    s.append("Self").append(',');
    s.append(person.attributes.get(Person.TELECOM)).append(",,");
    s.append(NEWLINE);
    write(s.toString(), spatientphone);

    if (person.random.nextBoolean()) {
      // Add an email address
      s.setLength(0);
      s.append(getNextKey(spatientphone)).append(',');
      s.append(personID).append(',');
      s.append("Patient Email").append(',');
      s.append(person.attributes.get(Person.FIRST_NAME)).append(' ');
      s.append(person.attributes.get(Person.LAST_NAME)).append(',');
      s.append("Self").append(',');
      s.append(",,");
      s.append(person.attributes.get(Person.FIRST_NAME)).append('.');
      s.append(person.attributes.get(Person.LAST_NAME)).append("@email.example");
      s.append(NEWLINE);
      write(s.toString(), spatientphone);
    }

    //patientrace.write("PatientRaceSID,PatientSID,Race");
    String race = (String) person.attributes.get(Person.RACE);
    if (race.equals("white")) {
      race = "WHITE NOT OF HISP ORIG";
    } else if (race.equals("hispanic")) {
      race = "WHITE";
    } else if (race.equals("black")) {
      race = "BLACK OR AFRICAN AMERICAN";
    } else if (race.equals("asian")) {
      race = "ASIAN";
    } else if (race.equals("native")) {
      if (person.attributes.get(Person.STATE).equals("Hawaii")) {
        race = "NATIVE HAWAIIAN OR OTHER PACIFIC ISLANDER";
      } else {
        race = "AMERICAN INDIAN OR ALASKA NATIVE";
      }
    } else { // race.equals("other")
      race = "ASIAN";
    }
    s.setLength(0);
    s.append(getNextKey(patientrace)).append(',');
    s.append(personID).append(',');
    s.append(race);
    s.append(NEWLINE);
    write(s.toString(), patientrace);

    //patientethnicity.write("PatientEthnicitySID,PatientSID,Ethnicity");
    s.setLength(0);
    s.append(getNextKey(patientethnicity)).append(',');
    s.append(personID).append(',');
    race = (String) person.attributes.get(Person.RACE);
    if (race.equals("hispanic")) {
      s.append("HISPANIC OR LATINO");
    } else {
      s.append("NOT HISPANIC OR LATINO");
    }
    s.append(NEWLINE);
    write(s.toString(), patientethnicity);

    return personID;
  }

  /**
   * Write a single Encounter line to encounters.csv.
   *
   * @param personID The ID of the person that had this encounter
   * @param person The person attending the encounter
   * @param encounter The encounter itself
   * @return The encounter ID, to be referenced as a "foreign key" if necessary
   * @throws IOException if any IO error occurs
   */
  private int encounter(int personID, Person person, Encounter encounter) throws IOException {
    // ID,START,STOP,PATIENT,CODE,DESCRIPTION,COST,REASONCODE,REASONDESCRIPTION
    StringBuilder s = new StringBuilder();

    // consult.write("ConsultSID,ToRequestServiceSID");
    int consultSid = getNextKey(consult);
    s.append(consultSid).append(',').append(consultSid).append(NEWLINE);
    write(s.toString(), consult);

    // visit.write("VisitSID,VisitDateTime,CreatedByStaffSID,LocationSID,PatientSID");
    int visitSid = getNextKey(visit);
    s.setLength(0);
    s.append(visitSid).append(',');
    s.append(iso8601Timestamp(encounter.start)).append(',');
    s.append(','); // CreatedByStaffID == null
    Integer locationSid = null;
    if (encounter.provider != null) {
      locationSid = location.addFact(encounter.provider.id, clean(encounter.provider.name));
      s.append(locationSid).append(',');
    } else {
      s.append(',');
    }
    s.append(personID);
    s.append(NEWLINE);
    write(s.toString(), visit);

    // appointment.write("AppointmentSID,Sta3n,PatientSID,AppointmentDateTime,AppointmentMadeDate,"
    //    + "AppointmentTypeSID,AppointmentStatus,VisitSID,LocationSID,PurposeOfVisit,"
    //    + "SchedulingRequestType,FollowUpVisitFlag,LengthOfAppointment,ConsultSID,"
    //    + "CheckInDateTime,CheckOutDateTime");
    s.setLength(0);
    s.append(getNextKey(appointment)).append(',');
    if (encounter.provider != null) {
      String state = Location.getStateName(encounter.provider.state);
      String tz = Location.getTimezoneByState(state);
      s.append(sta3n.addFact(encounter.provider.id, clean(encounter.provider.name) + "," + tz));
    }
    s.append(',');
    s.append(personID).append(',');
    s.append(iso8601Timestamp(encounter.start)).append(',');
    s.append(iso8601Timestamp(encounter.start)).append(',');
    s.append(",,"); // skip: AppointmentTypeSID, AppointmentStatus
    s.append(visitSid).append(',');
    if (locationSid != null) {
      s.append(locationSid).append(',');
    } else {
      s.append(",,");
    }
    s.append("3,"); // 3:SCHEDULED VISIT
    s.append(person.rand(new String[] {"N", "C", "P", "W", "M", "A", "O"})).append(',');
    s.append(person.randInt(1)).append(',');
    s.append((encounter.stop - encounter.start) / (60 * 1000)).append(',');
    s.append(consultSid).append(',');
    s.append(iso8601Timestamp(encounter.start)).append(',');
    s.append(iso8601Timestamp(encounter.stop)).append(NEWLINE);
    write(s.toString(), appointment);

    if (encounter.type.equalsIgnoreCase(EncounterType.INPATIENT.toString())) {
      // inpatient.write("InpatientSID,PatientSID,AdmitDateTime");
      s.setLength(0);
      s.append(getNextKey(inpatient)).append(',');
      s.append(personID).append(',');
      s.append(iso8601Timestamp(encounter.start)).append(NEWLINE);
      write(s.toString(), inpatient);
    }

    return visitSid;
  }

  /**
   * Write a single Condition to conditions.csv.
   *
   * @param personID ID of the person that has the condition.
   * @param encounterID ID of the encounter where the condition was diagnosed
   * @param condition The condition itself
   * @throws IOException if any IO error occurs
   */
  private void condition(int personID, int encounterID,
      Entry condition) throws IOException {
    // START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION
    StringBuilder s = new StringBuilder();

    s.append(dateFromTimestamp(condition.start)).append(',');
    if (condition.stop != 0L) {
      s.append(dateFromTimestamp(condition.stop));
    }
    s.append(',');
    s.append(personID).append(',');
    s.append(encounterID).append(',');

    Code coding = condition.codes.get(0);

    s.append(coding.code).append(',');
    s.append(clean(coding.display));

    s.append(NEWLINE);
    //write(s.toString(), conditions);
  }

  /**
   * Write a single Allergy to allergies.csv.
   *
   * @param personID ID of the person that has the allergy.
   * @param encounterID ID of the encounter where the allergy was diagnosed
   * @param allergy The allergy itself
   * @throws IOException if any IO error occurs
   */
  private void allergy(int personID, int encounterID,
      Entry allergy) throws IOException {
    // START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION
    StringBuilder s = new StringBuilder();

    s.append(dateFromTimestamp(allergy.start)).append(',');
    if (allergy.stop != 0L) {
      s.append(dateFromTimestamp(allergy.stop));
    }
    s.append(',');
    s.append(personID).append(',');
    s.append(encounterID).append(',');

    Code coding = allergy.codes.get(0);

    s.append(coding.code).append(',');
    s.append(clean(coding.display));

    s.append(NEWLINE);
    //write(s.toString(), allergies);
  }

  /**
   * Write a single Observation to observations.csv.
   *
   * @param personID ID of the person to whom the observation applies.
   * @param encounterID ID of the encounter where the observation was taken
   * @param observation The observation itself
   * @throws IOException if any IO error occurs
   */
  private void observation(int personID, int encounterID,
      Observation observation) throws IOException {

    if (observation.value == null) {
      if (observation.observations != null && !observation.observations.isEmpty()) {
        // just loop through the child observations

        for (Observation subObs : observation.observations) {
          observation(personID, encounterID, subObs);
        }
      }

      // no value so nothing more to report here
      return;
    }

    // DATE,PATIENT,ENCOUNTER,CODE,DESCRIPTION,VALUE,UNITS
    StringBuilder s = new StringBuilder();

    s.append(dateFromTimestamp(observation.start)).append(',');
    s.append(personID).append(',');
    s.append(encounterID).append(',');

    Code coding = observation.codes.get(0);

    s.append(coding.code).append(',');
    s.append(clean(coding.display)).append(',');

    String value = ExportHelper.getObservationValue(observation);
    String type = ExportHelper.getObservationType(observation);
    s.append(value).append(',');
    s.append(observation.unit).append(',');
    s.append(type);

    s.append(NEWLINE);
    //write(s.toString(), observations);
  }

  /**
   * Write a single Procedure to procedures.csv.
   *
   * @param personID ID of the person on whom the procedure was performed.
   * @param encounterID ID of the encounter where the procedure was performed
   * @param procedure The procedure itself
   * @throws IOException if any IO error occurs
   */
  private void procedure(int personID, int encounterID,
      Procedure procedure) throws IOException {
    // DATE,PATIENT,ENCOUNTER,CODE,DESCRIPTION,COST,REASONCODE,REASONDESCRIPTION
    StringBuilder s = new StringBuilder();

    s.append(dateFromTimestamp(procedure.start)).append(',');
    s.append(personID).append(',');
    s.append(encounterID).append(',');

    Code coding = procedure.codes.get(0);

    s.append(coding.code).append(',');
    s.append(clean(coding.display)).append(',');

    s.append(String.format("%.2f", Costs.calculateCost(procedure, true))).append(',');

    if (procedure.reasons.isEmpty()) {
      s.append(','); // reason code & desc
    } else {
      Code reason = procedure.reasons.get(0);
      s.append(reason.code).append(',');
      s.append(clean(reason.display));
    }

    s.append(NEWLINE);
    //write(s.toString(), procedures);
  }

  /**
   * Write a single Medication to medications.csv.
   *
   * @param personID ID of the person prescribed the medication.
   * @param encounterID ID of the encounter where the medication was prescribed
   * @param medication The medication itself
   * @throws IOException if any IO error occurs
   */
  private void medication(int personID, int encounterID,
      Medication medication) throws IOException {
    // START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION,COST,REASONCODE,REASONDESCRIPTION
    StringBuilder s = new StringBuilder();

    s.append(dateFromTimestamp(medication.start)).append(',');
    if (medication.stop != 0L) {
      s.append(dateFromTimestamp(medication.stop));
    }
    s.append(',');
    s.append(personID).append(',');
    s.append(encounterID).append(',');

    Code coding = medication.codes.get(0);

    s.append(coding.code).append(',');
    s.append(clean(coding.display)).append(',');

    s.append(String.format("%.2f", Costs.calculateCost(medication, true))).append(',');

    if (medication.reasons.isEmpty()) {
      s.append(','); // reason code & desc
    } else {
      Code reason = medication.reasons.get(0);
      s.append(reason.code).append(',');
      s.append(clean(reason.display));
    }

    s.append(NEWLINE);
    //write(s.toString(), medications);
  }

  /**
   * Write a single Immunization to immunizations.csv.
   *
   * @param personID ID of the person on whom the immunization was performed.
   * @param encounterID ID of the encounter where the immunization was performed
   * @param immunization The immunization itself
   * @throws IOException if any IO error occurs
   */
  private void immunization(int personID, int encounterID,
      Entry immunization) throws IOException  {
    // DATE,PATIENT,ENCOUNTER,CODE,DESCRIPTION,COST
    StringBuilder s = new StringBuilder();

    s.append(dateFromTimestamp(immunization.start)).append(',');
    s.append(personID).append(',');
    s.append(encounterID).append(',');

    Code coding = immunization.codes.get(0);

    s.append(coding.code).append(',');
    s.append(clean(coding.display)).append(',');

    s.append(String.format("%.2f", Costs.calculateCost(immunization, true)));

    s.append(NEWLINE);
    //write(s.toString(), immunizations);
  }

  /**
   * Write a single CarePlan to careplans.csv.
   *
   * @param personID ID of the person prescribed the careplan.
   * @param encounterID ID of the encounter where the careplan was prescribed
   * @param careplan The careplan itself
   * @throws IOException if any IO error occurs
   */
  private String careplan(int personID, int encounterID,
      CarePlan careplan) throws IOException {
    // ID,START,STOP,PATIENT,ENCOUNTER,CODE,DESCRIPTION,REASONCODE,REASONDESCRIPTION
    StringBuilder s = new StringBuilder();

    String careplanID = UUID.randomUUID().toString();
    s.append(careplanID).append(',');
    s.append(dateFromTimestamp(careplan.start)).append(',');
    if (careplan.stop != 0L) {
      s.append(dateFromTimestamp(careplan.stop));
    }
    s.append(',');
    s.append(personID).append(',');
    s.append(encounterID).append(',');

    Code coding = careplan.codes.get(0);

    s.append(coding.code).append(',');
    s.append(coding.display).append(',');

    if (careplan.reasons.isEmpty()) {
      s.append(','); // reason code & desc
    } else {
      Code reason = careplan.reasons.get(0);
      s.append(reason.code).append(',');
      s.append(clean(reason.display));
    }
    s.append(NEWLINE);

    //write(s.toString(), careplans);

    return careplanID;
  }

  /**
   * Write a single ImagingStudy to imaging_studies.csv.
   *
   * @param personID ID of the person the ImagingStudy was taken of.
   * @param encounterID ID of the encounter where the ImagingStudy was performed
   * @param imagingStudy The ImagingStudy itself
   * @throws IOException if any IO error occurs
   */
  private String imagingStudy(int personID, int encounterID,
      ImagingStudy imagingStudy) throws IOException {
    // ID,DATE,PATIENT,ENCOUNTER,BODYSITE_CODE,BODYSITE_DESCRIPTION,
    // MODALITY_CODE,MODALITY_DESCRIPTION,SOP_CODE,SOP_DESCRIPTION
    StringBuilder s = new StringBuilder();

    String studyID = UUID.randomUUID().toString();
    s.append(studyID).append(',');
    s.append(dateFromTimestamp(imagingStudy.start)).append(',');
    s.append(personID).append(',');
    s.append(encounterID).append(',');

    ImagingStudy.Series series1 = imagingStudy.series.get(0);
    ImagingStudy.Instance instance1 = series1.instances.get(0);

    Code bodySite = series1.bodySite;
    Code modality = series1.modality;
    Code sopClass = instance1.sopClass;

    s.append(bodySite.code).append(',');
    s.append(bodySite.display).append(',');

    s.append(modality.code).append(',');
    s.append(modality.display).append(',');

    s.append(sopClass.code).append(',');
    s.append(sopClass.display);

    s.append(NEWLINE);

    //write(s.toString(), imagingStudies);

    return studyID;
  }

  private int getNextKey(FileWriter table) {
    synchronized (sids) {
      return sids.computeIfAbsent(table, k -> new AtomicInteger(1)).getAndIncrement();
    }
  }
  
  /**
   * Replaces commas and line breaks in the source string with a single space.
   * Null is replaced with the empty string.
   */
  private static String clean(String src) {
    if (src == null) {
      return "";
    } else {
      return src.replaceAll("\\r\\n|\\r|\\n|,", " ").trim();
    }
  }

  /**
   * Helper method to write a line to a File.
   * Extracted to a separate method here to make it a little easier to replace implementations.
   *
   * @param line The line to write
   * @param writer The place to write it
   * @throws IOException if an I/O error occurs
   */
  private static void write(String line, FileWriter writer) throws IOException {
    synchronized (writer) {
      writer.write(line);
    }
  }
}
