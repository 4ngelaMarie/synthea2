module Synthea
  module Modules
    class Lifecycle < Synthea::Rules

      attr_accessor :male_growth, :male_weight, :female_growth, :female_weight
      attr_accessor :races, :ethnicity, :blood_types

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
          entity[:name_first] = "#{entity[:name_first]}#{(entity[:name_first].hash % 999)}"
          entity[:name_last] = Faker::Name.last_name
          entity[:name_last] = "#{entity[:name_last]}#{(entity[:name_last].hash % 999)}"
          entity[:gender] = gender unless entity[:gender]
          entity[:race] = Synthea::World::Demographics::RACES.pick unless entity[:race]
          entity[:ethnicity] = Synthea::World::Demographics::ETHNICITY[ entity[:race] ].pick unless entity[:ethnicity]
          entity[:blood_type] = Synthea::World::Demographics::BLOOD_TYPES[ entity[:race] ].pick
          # new babies are average weight and length for American newborns
          entity[:height] = 51 # centimeters
          entity[:weight] = 3.5 # kilograms
          entity[:is_alive] = true
          entity.events.create(time, :birth, :birth, true)
          entity.events.create(time, :encounter, :birth)

          #determine lat/long coordinates of address within MA
          location_data = Synthea::Location.selectPoint(entity[:city])
          entity[:coordinates_address] = location_data['point']
          zip_code = Synthea::Location.get_zipcode(location_data['city'])
          entity[:address] = {
            'line' => [ Faker::Address.street_address ],
            'city' => location_data['city'],
            'state' => "MA",
            'postalCode' => zip_code
          }
          entity[:address]['line'] << Faker::Address.secondary_address if (rand < 0.5)
          entity[:city] = location_data['city']
          
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
            dt = nil
            begin
              dt = DateTime.new(time.year,birthdate.month,birthdate.mday,birthdate.hour,birthdate.min,birthdate.sec,birthdate.formatted_offset)
            rescue Exception => e
              # this person was born on a leap-day
              dt = time
            end
            entity.events.create(dt.to_time, :grow, :age)
          end
          # TODO update awareness
        end
      end

      # People grow
      rule :grow, [:age,:is_alive,:gender], [:height,:weight,:bmi] do |time, entity|
        # Assume a linear growth rate until average size is achieved at age 20
        # TODO consider genetics, social determinants of health, etc
        if entity[:is_alive]
          unprocessed_events = entity.events.unprocessed.select{|e|e.type==:grow}
          unprocessed_events.each do |event|
            entity.events.process(event)
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
      end

      # People die
      rule :death, [:age], [] do |time, entity|
        unless entity.had_event?(:death)
          if(rand <= likelihood_of_death(entity[:age]))
            entity[:is_alive] = false
            entity.events.create(time, :death, :death, true)
            self.class.record_death(entity,time)
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
          x = 508.1/100000
        when age >= 1  && age <=4
          #15.6/100000/365
          x = 15.6/100000
        when age >= 5  && age <=14
          #10.6/100000/365
          x = 10.6/100000
        when age >= 15 && age <=24
          #56.4/100000/365
          x = 56.4/100000
        when age >= 25 && age <=34
          #74.7/100000/365
          x = 74.7/100000
        when age >= 35 && age <=44
          #145.7/100000/365
          x = 145.7/100000
        when age >= 45 && age <=54
          #326.5/100000/365
          x = 326.5/100000
        when age >= 55 && age <=64
          #737.8/100000/365
          x = 737.8/100000
        when age >= 65 && age <=74
          #1817.0/100000/365
          x = 1817.0/100000
        when age >= 75 && age <=84
          #4877.3/100000/365
          x = 4877.3/100000
        when age >= 85 && age <=94
          #13499.4/100000/365
          x = 13499.4/100000
        else
          #50000/100000/365
          x = 50000.to_f/100000
        end
        return Synthea::Rules.convert_risk_to_timestep(x,365)
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

      #------------------------------------------------------------------------# begin class record functions
      def self.record_death(entity, time)
        entity.record_synthea.death(time)
      end

      def self.record_height_weight(entity, time)
        entity.record_synthea.observation(:weight, time, entity[:weight], :observation, :vital_sign)
        entity.record_synthea.observation(:height, time, entity[:height], :observation, :vital_sign)
      end
    end
  end
end
