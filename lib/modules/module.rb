module Synthea
  class Rules

    cattr_accessor :metadata

    def initialize
      @rules ||= methods.grep(/_rule$/).map {|r| method(r)}
    end

    def run(time, entity)
      @rules.each {|r| r.call(time, entity)}
    end

    def pick(array)
      rand(array.first..array.last)
    end

    def self.apply(time,entity)
      get_modules.each {|r| r.run(time, entity)}
    end

    def self.get_modules
      @@modules ||= Synthea::Modules.constants.map {|m| "Synthea::Modules::#{m}".constantize.new}
    end

    def self.rule(name,inputs,outputs,&block)
      @@metadata ||= {}
      @@metadata[name] = {
        inputs: inputs,
        outputs: outputs,
        module_name: self.to_s.split('::').last
      }
      define_method "#{name}_rule".to_sym, block
    end

    #Let Y be the original period risk (in our example 3650 days) and X be the time-step risk. The chance of an event not happening in 10 years is the 
    #probability of the event not occuring every time step. (1-X)^(3650/time_step). Subtract this from 1 to get the
    #probability of the event occuring in 10 years: Y = 1-(1-X)^(3650/time_step). Solve the equation for X to yield the
    #formula below:

    #original_period_days is the time period of the original risk in days. Risk is the risk probability
    def self.convert_risk_to_timestep(risk, original_period)
      return 1-((1-risk) ** (Synthea::Config.time_step.to_f/original_period))
    end

    def prescribeMedication(med, reason, time, entity)
      if entity[:medications][med].nil?
        entity[:medications][med] = [ time, [reason ] ]
      elsif !entity[:medications][med][1].include?(reason) 
        entity[:medications][med][1] << reason
      end
    end

    def stopMedication(med, reason, time, entity)
      return if entity[:medications][med].nil?
      entity[:medications][med][1].delete(reason) if entity[:medications][med][1].include?(reason)
      entity[:medications].delete(med) if entity[:medications][med][1].empty?
    end
  end
end