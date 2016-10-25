source 'https://rubygems.org'
gemspec

gem 'rake'
gem 'pry'
gem 'tilt'
gem 'faker'
gem 'distribution'
gem 'pickup'
gem 'recursive-open-struct'
gem 'health-data-standards', git: 'https://github.com/projectcypress/health-data-standards.git', branch: 'master'
gem 'fhir_models', '>= 1.6'
gem 'fhir_client' # , path: '../fhir_client'
gem 'georuby'
gem 'net-sftp'
gem 'concurrent-ruby', require: 'concurrent'
gem 'rack', '~> 1.6' # locked at 1.6 to maintain compatibility with ruby 2.0.0

group :test do
  gem 'rubocop', '~> 0.43.0', require: false
  gem 'cane', '~> 2.3.0'
  gem 'simplecov', require: false
  gem 'minitest', '~> 5.3'
  gem 'minitest-reporters'
  gem 'awesome_print', require: 'ap'
end
