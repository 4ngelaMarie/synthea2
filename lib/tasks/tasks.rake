namespace :synthea do
  
  desc 'console'
  task :console, [] do |t, args|
    binding.pry
  end

  desc 'generate'
  task :generate, [] do |t, args|
    start = Time.now
    world = Synthea::World::Population.new
    world.run
    finish = Time.now
    minutes = ((finish-start)/60)
    seconds = (minutes - minutes.floor) * 60
    puts "Completed in #{minutes.floor} minute(s) #{seconds.floor} second(s)."

    binding.pry

    puts "Saving patient records..."
    export(world.people | world.dead)
  end

  def export(patients)
    # we need to configure mongo to export for some reason... not ideal
    Mongoid.configure { |config| config.connect_to("synthea_test") }

    out_dir = File.join('output','html')
    FileUtils.rm_r out_dir if File.exists? out_dir
    FileUtils.mkdir_p out_dir
    patients.each do |patient|
      html = HealthDataStandards::Export::HTML.new.export(patient.record)
      File.open(File.join(out_dir, "#{patient.attributes[:name_last]}_#{patient.attributes[:name_first]}_#{!patient.attributes[:diabetes].nil?}.html"), 'w') { |file| file.write(html) }
    end
  end

end
