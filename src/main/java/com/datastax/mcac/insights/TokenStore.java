package com.datastax.mcac.insights;

/*
 *
 * @author Sebastián Estévez on 8/30/19.
 *
 */


import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public interface TokenStore
{
    class InMemory implements TokenStore
    {
        private final AtomicReference<String> tokenRef = new AtomicReference<>();

        public boolean isEmpty()
        {
            return tokenRef.get() == null;
        }

        public Optional<String> token()
        {
            return Optional.ofNullable(tokenRef.get());
        }

        public boolean store(String token)
        {
            return tokenRef.compareAndSet(tokenRef.get(), token);
        }
    }

    Optional<String> token();

    boolean isEmpty();

    boolean store(String token);

}