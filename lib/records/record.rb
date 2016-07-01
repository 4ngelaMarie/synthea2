module Synthea
	module Output
		class Record
			attr_accessor :patient_info, :encounters, :observations, :conditions, :present, :procedures, :immunizations
			def initialize
				@patient_info = {expired: false}
				@encounters = []
				@observations = []
				#store condition info
				@conditions = []
				#check presence of condition
				@present = {}
        @procedures = []
        @immunizations = []
			end
			#birth and basic information are stored as person attributes and can be referred to when writing other records.
			#No need to duplicate here.
			def death(time)
				@patient_info[:deathdate] =  time
				@patient_info[:expired] = true
			end

			def observation(type, time, value, fhir_method, ccda_method)
        @observations << {
          'type' => type,
          'time' => time,
          'value' => value,
          'fhir' => fhir_method, 
          'ccda' => ccda_method
        }
      end

      def condition(type, time, fhir_method, ccda_method)
        @present[type] = {
          'type' => type,
          'time' => time,
          'fhir' => fhir_method,
          'ccda' => ccda_method
        }
        @conditions << @present[type]
      end

      def end_condition(type, time)
        @present[type]['end_time'] = time
        @present[type] = nil
      end

      def procedure(type, time, reason, fhir_method, ccda_method)
        @present[type] = {
          'type' => type,
          'time' => time,
          'reason' => reason,
          'fhir' => fhir_method,
          'ccda' => ccda_method
        }
        @procedures << @present[type]
      end

      def diagnostic_report(type, time, numObs, fhir_method, ccda_method)
        @observations << {
          'type' => type,
          'time' => time,
          'numObs' => numObs,
          'fhir' => fhir_method,
          'ccda' => ccda_method
        }
      end

      def encounter(type, time) 
        @encounters << {
          'type' => type,
          'time' => time
        }
      end

      def immunization(imm, time, fhir_method, ccda_method)
        @immunizations << {
          'type' => imm,
          'time' => time,
          'fhir' => fhir_method,
          'ccda' => ccda_method
        }
      end
		end
	end
end