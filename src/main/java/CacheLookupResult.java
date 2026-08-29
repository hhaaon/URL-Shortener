public sealed interface CacheLookupResult
    permits CacheLookupResult.Hit,
            CacheLookupResult.NegativeHit,
            CacheLookupResult.Miss {
        record Hit(ShortUrl shortUrl) implements CacheLookupResult{
        }

        record NegativeHit() implements CacheLookupResult {
        }

        record Miss() implements CacheLookupResult{
        }
    }
