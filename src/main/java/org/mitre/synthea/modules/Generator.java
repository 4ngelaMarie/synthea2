package org.mitre.synthea.modules;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.mitre.synthea.datastore.InMemoryDatabase;
import org.mitre.synthea.export.Exporter;
import org.mitre.synthea.export.HospitalExporter;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.Hospital;
import org.mitre.synthea.world.Demographics;
import org.mitre.synthea.world.Location;

/**
 * Generator creates a population by running the generic modules each timestep per Person.
 */
public class Generator {

	public final long ONE_HUNDRED_YEARS = 100l * TimeUnit.DAYS.toMillis(365);
	public InMemoryDatabase people; //List<Person> people;
	public List<CommunityHealthWorker> chws;
	public long numberOfPeople;
	public final int MAX_TRIES = 10;
	public long seed;
	private Random random;
	public long timestep;
	public long stop;
	public Map<String,AtomicInteger> stats;
	public Map<String,Demographics> demographics;
	
	public Generator(int people) throws IOException
	{
		init(people, System.currentTimeMillis());
	}
	
	public Generator(int people, long seed) throws IOException
	{
		init(people, seed);
	}
	
	private void init(int people, long seed) throws IOException
	{
		this.people =  InMemoryDatabase.getInstance(); //Collections.synchronizedList(new ArrayList<Person>());
		this.numberOfPeople = people;
		this.chws = Collections.synchronizedList(new ArrayList<CommunityHealthWorker>());
		this.seed = seed;
		this.random = new Random(seed);
		this.timestep = Long.parseLong( Config.get("generate.timestep") );
		this.stop = System.currentTimeMillis();
		this.demographics = Demographics.loadByName( Config.get("generate.demographics.default_file") );
		
		this.stats = Collections.synchronizedMap(new HashMap<String,AtomicInteger>());
		stats.put("alive", new AtomicInteger(0));
		stats.put("dead", new AtomicInteger(0));
		
		// initialize hospitals
		Hospital.loadHospitals();
		Module.getModules(); // ensure modules load early
		CommunityHealthWorker.getCost(); // ensure CHWs are set early
	}
	
	public void run()
	{
		ExecutorService threadPool = Executors.newFixedThreadPool(8);
		
		for(int i=0; i < this.numberOfPeople; i++)
		{
			final int index = i;
			threadPool.submit( () -> generatePerson(index) );
		}

		try 
		{
			threadPool.shutdown();
			while (!threadPool.awaitTermination(30, TimeUnit.SECONDS))
			{
				System.out.println("Waiting for threads to finish... " + threadPool);
			}
		} catch (InterruptedException e)
		{
			e.printStackTrace();
		}
		
		// export hospital information
		try{
			HospitalExporter.export(stop);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		System.out.println(stats);
	
	}
	
	
	private void generatePerson(int index)
	{
		try 
		{
			boolean isAlive = true;
			String cityName = Location.randomCityName(random);
			Demographics city = demographics.get(cityName);
			if (city == null && cityName.endsWith(" Town"))
			{
				cityName = cityName.substring(0, cityName.length() - 5);
				city = demographics.get(cityName);
			}
			
			do
			{
				List<Module> modules = Module.getModules();
				
				// System.currentTimeMillis is not unique enough
				long personSeed = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
				Person person = new Person(personSeed);
				
				// TODO - this is quick & easy to implement,
				// but we need to adapt the ruby method of pre-defining all the demographic buckets
				// and then putting people into those
				// -- but: how will that work with seeds?
				long start = setDemographics(person, cityName, city);
			
				LifecycleModule.birth(person, start);
				EncounterModule encounterModule = new EncounterModule();

				
					
				long time = start;
				while(person.alive(time) && time < stop)
				{
					encounterModule.process(person, time);
					Iterator<Module> iter = modules.iterator();
					while(iter.hasNext())
					{
						Module module = iter.next();
		//				System.out.format("Processing module %s\n", module.name);
						if(module.process(person, time))
						{
		//					System.out.format("Removing module %s\n", module.name);
							iter.remove(); // this module has completed/terminated.
						}
					}
					encounterModule.endWellnessEncounter(person, time);

					// TODO: if CHW policy is enabled for community, possibly add CHW interventions
					// if true
					// then add chw encounter to record
					// and set chw variable(s) on person.attributes.put(KEY, VALUE)
			
					
					time += timestep;
				}
				
				Exporter.export(person, stop);
				if (people != null)
				{
					people.store(person);
				}
				
				isAlive = person.alive(time);
				
				String deceased = isAlive ? "" : "DECEASED";
				System.out.format("%d -- %s (%d y/o) %s %s\n", index+1, person.attributes.get(Person.NAME), person.ageInYears(time), person.attributes.get(Person.CITY), deceased);
				
				String key = isAlive ? "alive" : "dead";
				
				AtomicInteger count = stats.get(key);
				count.incrementAndGet();
			} while (!isAlive);
		} catch (Throwable e) // lots of fhir things throw errors for some reason
		{
			e.printStackTrace();
			throw e;
		}
	}
	
	private long setDemographics(Person person, String cityName, Demographics city)
	{
		person.attributes.put(Person.CITY, cityName);

		String race = city.pickRace(person.random);
		person.attributes.put(Person.RACE, race);
		
		String gender = city.pickGender(person.random);
		if (gender.equalsIgnoreCase("male") || gender.equalsIgnoreCase("M"))
		{
			gender = "M";
		} else
		{
			gender = "F";
		}
		person.attributes.put(Person.GENDER, gender);

		// Socioeconomic variables of education, income, and education are set.
		String education = city.pickEducation(person.random);
		person.attributes.put(Person.EDUCATION, education);
		double education_level = city.educationLevel(education, person);
		person.attributes.put(Person.EDUCATION_LEVEL, education_level);

		int income = city.pickIncome(person.random);
		person.attributes.put(Person.INCOME, income);
		double income_level = city.incomeLevel(income);
		person.attributes.put(Person.INCOME_LEVEL, income_level);

		double occupation = person.rand();
		person.attributes.put(Person.OCCUPATION_LEVEL, occupation);

		double ses_score = city.socioeconomicScore(income_level, education_level, occupation);
		person.attributes.put(Person.SOCIOECONOMIC_SCORE, ses_score);
		person.attributes.put(Person.SOCIOECONOMIC_CATEGORY, city.socioeconomicCategory(ses_score));
		
		long targetAge = city.pickAge(person.random);
		
		// TODO this is terrible date handling, figure out how to use the java time library
        long earliestBirthdate = stop - TimeUnit.DAYS.toMillis((targetAge + 1) * 365L + 1);
        long latestBirthdate = stop - TimeUnit.DAYS.toMillis(targetAge * 365L);

        long birthdate = (long) person.rand(earliestBirthdate, latestBirthdate);
		
		return birthdate;
	}
}
