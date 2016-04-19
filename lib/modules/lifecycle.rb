module Synthea
  module Modules
    class Lifecycle < Synthea::Rules

      attr_accessor :male_growth, :male_weight, :female_growth, :female_weight

      def initialize
        super
        @male_growth = Distribution::Normal.rng(Synthea::Config.lifecycle.growth_rate_male_average,Synthea::Config.lifecycle.growth_rate_male_stddev)
        @male_weight = Distribution::Normal.rng(Synthea::Config.lifecycle.weight_gain_male_average,Synthea::Config.lifecycle.weight_gain_male_stddev)
        @female_growth = Distribution::Normal.rng(Synthea::Config.lifecycle.growth_rate_female_average,Synthea::Config.lifecycle.growth_rate_female_stddev)
        @female_weight = Distribution::Normal.rng(Synthea::Config.lifecycle.weight_gain_female_average,Synthea::Config.lifecycle.weight_gain_female_stddev)     
      end

      # People are born
      rule :birth, [], [:age,:is_alive] do |time, entity|
        unless entity.had_event?(:birth)
          entity[:age] = 0
          entity[:name_first] = Faker::Name.first_name
          entity[:name_last] = Faker::Name.last_name
          entity[:gender] = gender
          # new babies are average weight and length for American newborns
          entity[:height] = 51 # centimeters
          entity[:weight] = 3.5 # kilograms
          entity[:is_alive] = true
          entity.events.create(time, :birth, :birth, true)
          entity.events.create(time, :encounter_ordered, :birth)

          Record.birth(entity, time)
          # TODO update record
          # TODO update awareness
        end
      end

      # People age
      rule :age, [:birth,:age,:is_alive], [:age] do |time, entity|
        if entity[:is_alive]
          birthdate = entity.event(:birth).time
          age = entity[:age]
          entity[:age] = ((time.to_i - birthdate.to_i)/1.year).floor
          if(entity[:age] > age)
            entity.events.create(time, :grow, :age)
          end
          # TODO update record
          # TODO update awareness
        end
      end

      # People grow
      rule :grow, [:age,:is_alive,:gender], [:height,:weight,:bmi] do |time, entity|
        # Assume a linear growth rate until average size is achieved at age 20
        # TODO consider genetics, social determinants of health, etc
        while entity[:is_alive] && entity.events(:grow).unprocessed.next?
          event = entity.events(:grow).unprocessed.next
          event.processed=true
          age = entity[:age]
          gender = entity[:gender]
          if(age <= 20)
            if(gender=='M')
              entity[:height] += @male_growth.call # centimeters
              entity[:weight] += @male_weight.call # kilograms
            elsif(gender=='F')
              entity[:height] += @female_growth.call # centimeters
              entity[:weight] += @female_weight.call # kilograms
            end
          elsif(age <= Synthea::Config.lifecycle.adult_max_weight_age)
            # getting older and fatter
            if(gender=='M')
              entity[:weight] *= (1 + Synthea::Config.lifecycle.adult_male_weight_gain)
            elsif(gender=='F')
              entity[:weight] *= (1 + Synthea::Config.lifecycle.adult_female_weight_gain)
            end           
          else
            # TODO random change in weight?
          end
          # set the BMI
          entity[:bmi] = calculate_bmi(entity[:height],entity[:weight])
        end        
      end

      # People die
      rule :death, [:age], [] do |time, entity|
        unless entity.had_event?(:death)
          if(rand <= likelihood_of_death(entity[:age]))
            entity[:is_alive] = false
            entity.events.create(time, :death, :death, true)
            Record.death(entity, time)
          end
        end
      end

      def gender(ratios = {male: 0.5})
        value = rand
        case 
          when value < ratios[:male]
            'M'
          else
            'F'
        end
      end

      # height in centimeters
      # weight in kilograms
      def calculate_bmi(height,weight)
        ( weight / ( (height/100) * (height/100) ) )
      end

      def likelihood_of_death(age)
        # http://www.cdc.gov/nchs/nvss/mortality/gmwk23r.htm: 820.4/100000
        case 
        when age < 1
          #508.1/100000/365
          0.00001392054794520548
        when age >= 1  && age <=4
          #15.6/100000/365
          0.0000004273972602739726
        when age >= 5  && age <=14
          #10.6/100000/365
          0.0000002904109589041096
        when age >= 15 && age <=24
          #56.4/100000/365
          0.0000015452054794520548
        when age >= 25 && age <=34
          #74.7/100000/365
          0.0000020465753424657535
        when age >= 35 && age <=44
          #145.7/100000/365
          0.000003991780821917808
        when age >= 45 && age <=54
          #326.5/100000/365
          0.000008945205479452055
        when age >= 55 && age <=64
          #737.8/100000/365
          0.000020213698630136987
        when age >= 65 && age <=74
          #1817.0/100000/365
          0.00004978082191780822
        when age >= 75 && age <=84
          #4877.3/100000/365
          0.00013362465753424658
        when age >= 85 && age <=94
          #13499.4/100000/365
          0.00036984657534246574
        else
          #50000/100000/365
          0.0013698630136986301
        end
      end

      def self.age(time, birthdate, deathdate, unit=:years)
        case unit
        when :months
          left = deathdate.nil? ? time : deathdate
          (left.month - birthdate.month) + (12 * (left.year - birthdate.year)) + (left.day < birthdate.day ? -1 : 0)
        else
          divisor = 1.method(unit).call

          left = deathdate.nil? ? time : deathdate
          ((left - birthdate)/divisor).floor
        end
      end

      class Record < BaseRecord
        def self.birth(entity, time)
          patient = entity.record
          patient.first = entity[:name_first]
          patient.last = entity[:name_last]
          patient.gender = entity[:gender]
          patient.birthdate = time.to_i

          patient.deathdate = nil
          patient.expired = false

          # patient.religious_affiliation
          # patient.effective_time
          # patient.race
          # patient.ethnicity
          # patient.languages
          # patient.marital_status
          # patient.medical_record_number
          # patient.medical_record_assigner
        end

        def self.death(entity, time)
          patient = entity.record
          patient.deathdate = time.to_i
          patient.expired = true
        end

        def self.height_weight(entity, time)
          patient = entity.record
          patient.vital_signs << VitalSign.new(lab_hash(:weight, time, entity[:weight]))
          patient.vital_signs << VitalSign.new(lab_hash(:height, time, entity[:height]))
        end

      end


    end
  end
end
