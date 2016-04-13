module Synthea
  module Modules
    class Encounters < Synthea::Rules

      # People have encounters
      rule :schedule_encounter, [:age], [:encounter] do |time, entity|
        if entity.components[:is_alive]
          while entity.events(:encounter_ordered).unprocessed.next?

            event = entity.events(:encounter_ordered).unprocessed.next
            event.processed=true

            schedule_variance = Synthea::Config.schedule.variance
            birthdate = entity.event(:birth).time
            deathdate = entity.event(:death).try(:time)

            age_in_years = entity.attributes[:age]
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
            entity.events << Synthea::Event.new(next_date,:encounter,:schedule_encounter)
          end
        end
      end

      rule :encounter, [], [:schedule_encounter] do |time, entity|
        if entity.components[:is_alive]
          while (event = entity.events(:encounter).unprocessed.before(time).next)
            event.processed=true
            age = entity.attributes[:age]
            codes = case
              when age <= 1  
                {"CPT" => ["99391"], "ICD-9-CM" => ['V20.2'], "ICD-10-CM" => ['Z00.129'], 'SNOMED-CT' => ['170258001']}
              when age <= 4  
                {"CPT" => ["99392"], "ICD-9-CM" => ['V20.2'], "ICD-10-CM" => ['Z00.129'], 'SNOMED-CT' => ['170258001']}
              when age <= 11 
                {"CPT" => ["99393"], "ICD-9-CM" => ['V20.2'], "ICD-10-CM" => ['Z00.129'], 'SNOMED-CT' => ['170258001']}
              when age <= 17 
                {"CPT" => ["99394"], "ICD-9-CM" => ['V20.2'], "ICD-10-CM" => ['Z00.129'], 'SNOMED-CT' => ['170258001']}
              when age <= 39 
                {"CPT" => ["99395"], "ICD-9-CM" => ['V70.0'], "ICD-10-CM" => ['Z00.00'],  'SNOMED-CT' => ['185349003']}
              when age <= 64 
                {"CPT" => ["99396"], "ICD-9-CM" => ['V70.0'], "ICD-10-CM" => ['Z00.00'],  'SNOMED-CT' => ['185349003']}
              else
                {"CPT" => ["99397"], "ICD-9-CM" => ['V70.0'], "ICD-10-CM" => ['Z00.00'],  'SNOMED-CT' => ['185349003']} 
            end

            entity.record.encounters << Encounter.new({
              "codes" => codes,
              "description" => "Encounter, Performed: Outpatient Encounter",
              "start_time" => time.to_i,
              "end_time" => time.to_i + 15.minutes,
              "oid" => "2.16.840.1.113883.3.560.1.79"
            })

            entity.events << Synthea::Event.new(time,:encounter_ordered,:encounter)
          end
        end
      end

    end
  end
end