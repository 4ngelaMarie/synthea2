module Synthea
  module Modules
    class MetabolicSyndrome < Synthea::Rules

      # People have a BMI that we can roughly use to estimate
      # blood glucose and diabetes
      rule :prediabetes?, [:bmi], [:blood_glucose,:prediabetic] do |time, entity|
        bmi = entity.attributes[:bmi]
        if bmi
          entity.attributes[:blood_glucose] = blood_glucose(bmi)
          if(entity.attributes[:blood_glucose] < 6.5)
            entity.attributes.delete(:prediabetic)
          else
            entity.attributes[:prediabetic]=true 
            entity.events << Synthea::Event.new(time,:prediabetic,:prediabetes?,false) if !entity.had_event?(:prediabetic)
          end
        end
      end

      # rough linear fit seen in Figure 1
      # http://www.microbecolhealthdis.net/index.php/mehd/article/viewFile/22857/34046/125897
      def blood_glucose(bmi)
        ((bmi - 6) / 6.5)
      end

      class Record < BaseRecord
        def self.diagnoses(entity, time)
          patient = entity.record
          if entity.attributes[:prediabetic] && !entity.record_conditions[:prediabetes]
            # create the ongoing diagnosis
            entity.record_conditions[:prediabetes] = Condition.new(condition_hash(:prediabetes, time))
            patient.conditions << entity.record_conditions[:prediabetes]
          elsif !entity.attributes[:prediabetic] && entity.record_conditions[:prediabetes]
            # end the diagnosis
            entity.record_conditions[:prediabetes].end_time = time.to_i
            entity.record_conditions[:prediabetes] = nil
          end
        end
      end

    end
  end
end
