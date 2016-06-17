module Synthea
  module Modules
    class Encounters < Synthea::Rules

      # People have encounters
      rule :schedule_encounter, [:age], [:encounter] do |time, entity|
        if entity[:is_alive]
          unprocessed_events = entity.events(:encounter_ordered).unprocessed
          unprocessed_events.each do |event|
            event.processed=true

            schedule_variance = Synthea::Config.schedule.variance
            birthdate = entity.event(:birth).time
            deathdate = entity.event(:death).try(:time)

            age_in_years = entity[:age]
            if age_in_years >= 3
              delta = case 
                when age_in_years <= 19
                  1.year
                when age_in_years <= 39 
                  3.years
                when age_in_years <= 49 
                  2.years
                else 
                  1.year
              end
            else
              age_in_months = Synthea::Modules::Lifecycle.age(time, birthdate, deathdate, :months)
              delta = case 
                when age_in_months <= 1
                  1.months
                when age_in_months <= 5
                  2.months
                when age_in_months <= 17
                  3.months
                else 
                  6.months
              end
            end
            next_date = time + Distribution::Normal.rng(delta, delta*schedule_variance).call
            entity.events.create(next_date, :encounter, :schedule_encounter)
          end
        end
      end

      rule :encounter, [], [:schedule_encounter,:observations,:lab_results,:diagnoses,:immunizations] do |time, entity|
        if entity[:is_alive]
          unprocessed_events = entity.events(:encounter).unprocessed.before(time)
          unprocessed_events.each do |event|
            event.processed=true
            Record.encounter(entity, event.time)
            Synthea::Modules::Lifecycle::Record.height_weight(entity, event.time)
            Synthea::Modules::Immunizations::Record.perform_encounter(entity, event.time)
            Synthea::Modules::MetabolicSyndrome::Record.perform_encounter(entity, event.time)
            Synthea::Modules::FoodAllergies::Record.diagnoses(entity, event.time)
            Synthea::Modules::CardiovascularDisease::Record.perform_encounter(entity, event.time)
            entity.events.create(event.time, :encounter_ordered, :encounter)
          end

        end

      end

      #processes all emergency events. Implemented as a function instead of a rule because emergency events must be procesed
      #immediately rather than waiting til the next time period. Patient may die, resulting in rule not being called.
      def self.emergency_visit (time, entity)
        unprocessed_events = entity.events(:emergency_encounter).unprocessed.before(time)
        unprocessed_events.each do |event|
          event.processed=true
          Record.emergency_encounter(entity, event.time)
        end

        unprocessed_events = entity.events.select{|x| [:myocardial_infarction,:cardiac_arrest,:stroke].include?(x.type) }.unprocessed.before(time)
        unprocessed_events.each do |event|
          event.processed = true
          Synthea::Modules::CardiovascularDisease::Record.perform_emergency(entity, event)
        end
      end

      class Record < BaseRecord
        def self.encounter(entity, time)
          age = entity[:age]
          # https://www.uhccommunityplan.com/content/dam/communityplan/healthcareprofessionals/reimbursementpolicies/Preventive-Medicine-and-Screening-Policy-(R0013).pdf
          # https://www.aap.org/en-us/professional-resources/practice-support/financing-and-payment/documents/bf-pmsfactsheet.pdf
          # https://www.nlm.nih.gov/research/umls/mapping_projects/icd9cm_to_snomedct.html
          # http://icd10coded.com/convert/
          codes = case
            when age <= 1  
              {"ICD-9-CM" => ['V20.2'], "ICD-10-CM" => ['Z00.129'], 'SNOMED-CT' => ['170258001']}
            when age <= 4  
              {"ICD-9-CM" => ['V20.2'], "ICD-10-CM" => ['Z00.129'], 'SNOMED-CT' => ['170258001']}
            when age <= 11 
              {"ICD-9-CM" => ['V20.2'], "ICD-10-CM" => ['Z00.129'], 'SNOMED-CT' => ['170258001']}
            when age <= 17 
              {"ICD-9-CM" => ['V20.2'], "ICD-10-CM" => ['Z00.129'], 'SNOMED-CT' => ['170258001']}
            when age <= 39 
              {"ICD-9-CM" => ['V70.0'], "ICD-10-CM" => ['Z00.00'],  'SNOMED-CT' => ['185349003']}
            when age <= 64 
              {"ICD-9-CM" => ['V70.0'], "ICD-10-CM" => ['Z00.00'],  'SNOMED-CT' => ['185349003']}
            else
              {"ICD-9-CM" => ['V70.0'], "ICD-10-CM" => ['Z00.00'],  'SNOMED-CT' => ['185349003']} 
          end

          entity.record.encounters << Encounter.new(encounter_hash(time, codes))

          entity.fhir_record.entry << create_fhir_encounter('outpatient', entity, time, codes)
        end

        def self.emergency_encounter(entity,time)
          #Should probably add ICD codes
          codes = {'SNOMED-CT' => ['50849002']}
          encounter_data = encounter_hash(time, codes)
          encounter_data['description'] = "Emergency Encounter"
          entity.record.encounters << Encounter.new(encounter_data)
          entity.fhir_record.entry << create_fhir_encounter('emergency', entity, time, codes)
        end

        def self.create_fhir_encounter(type, entity, time, codes)
          entry = FHIR::Bundle::Entry.new
          encounter = FHIR::Encounter.new
          entry.fullUrl = SecureRandom.uuid.to_s
          encounter.status = 'finished'
          encounter.local_class = type
          encounterCode = FHIR::CodeableConcept.new({'coding' => [FHIR::Coding.new({'code' => codes['SNOMED-CT'][0], 'system'=>'http://snomed.info/sct'})]})
          encounter.type << encounterCode
          patient = entity.fhir_record.entry.find{|e| e.resource.is_a?(FHIR::Patient)}
          encounter.patient = FHIR::Reference.new({'reference'=>'Patient/' + patient.fullUrl})
          startTime = convertFhirDateTime(time,'time')
          endTime = convertFhirDateTime(time+15.minutes, 'time')
          encounter.period = FHIR::Period.new({'start' => startTime, 'end' => endTime})
          entry.resource = encounter
          return entry
        end

      end


    end
  end
end
