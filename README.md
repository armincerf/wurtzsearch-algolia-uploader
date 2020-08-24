# wurtzsearch

Takes questions from billwurtz.com/questions and adds them to an algolia index so they can be searched with a client like [this one](https://github.com/armincerf/WurtzSearchClient)

## Installation

clone this project

install clojure: https://clojure.org/guides/getting_started

run with `clojure -m armincerf.wurtzsearch APP-ID API-KEY` (get your app id and api key from signing up for an [algolia account](https://www.algolia.com/)

### Bugs

_Might_ be missing a couple of questions due to the janky way I'm scraping the site. But the lack of markup on bills page really doesn't make things easy and I'm trying to get this done quickly

Its also probably quite inefficient, but there aren't enough questions to warrant any real efforts there

## License

Copyright © 2020 Alex Davis

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
