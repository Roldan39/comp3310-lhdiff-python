/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.commons.io.serialization;

import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.util.regex.Pattern;

import org.apache.commons.io.build.AbstractStreamBuilder;


public class ValidatingObjectInputStream extends ObjectInputStream {

    // @formatter:off
    
    // @formatter:on
    public static class Builder extends AbstractStreamBuilder<ValidatingObjectInputStream, Builder> {

        private ObjectStreamClassPredicate predicate = new ObjectStreamClassPredicate();

        
        @Deprecated
        public Builder() {
            // empty
        }

        
        public Builder accept(final Class<?>... classes) {
            predicate.accept(classes);
            return this;
        }

        
        public Builder accept(final ClassNameMatcher matcher) {
            predicate.accept(matcher);
            return this;
        }

        
        public Builder accept(final Pattern pattern) {
            predicate.accept(pattern);
            return this;
        }

        
        public Builder accept(final String... patterns) {
            predicate.accept(patterns);
            return this;
        }

        
        @Override
        public ValidatingObjectInputStream get() throws IOException {
            return new ValidatingObjectInputStream(this);
        }

        
        public ObjectStreamClassPredicate getPredicate() {
            return predicate;
        }

        
        public Builder reject(final Class<?>... classes) {
            predicate.reject(classes);
            return this;
        }

        
        public Builder reject(final ClassNameMatcher matcher) {
            predicate.reject(matcher);
            return this;
        }

        
        public Builder reject(final Pattern pattern) {
            predicate.reject(pattern);
            return this;
        }

        
        public Builder reject(final String... patterns) {
            predicate.reject(patterns);
            return this;
        }

        
        public Builder setPredicate(final ObjectStreamClassPredicate predicate) {
            this.predicate = predicate != null ? predicate : new ObjectStreamClassPredicate();
            return this;
        }

    }

    
    public static Builder builder() {
        return new Builder();
    }

    private final ObjectStreamClassPredicate predicate;

    @SuppressWarnings("resource") // caller closes/
    private ValidatingObjectInputStream(final Builder builder) throws IOException {
        this(builder.getInputStream(), builder.predicate);
    }

    
    @Deprecated
    public ValidatingObjectInputStream(final InputStream input) throws IOException {
        this(input, new ObjectStreamClassPredicate());
    }

    
    private ValidatingObjectInputStream(final InputStream input, final ObjectStreamClassPredicate predicate) throws IOException {
        super(input);
        this.predicate = predicate;
    }

    
    public ValidatingObjectInputStream accept(final Class<?>... classes) {
        predicate.accept(classes);
        return this;
    }

    
    public ValidatingObjectInputStream accept(final ClassNameMatcher matcher) {
        predicate.accept(matcher);
        return this;
    }

    
    public ValidatingObjectInputStream accept(final Pattern pattern) {
        predicate.accept(pattern);
        return this;
    }

    
    public ValidatingObjectInputStream accept(final String... patterns) {
        predicate.accept(patterns);
        return this;
    }

    
    private void checkClassName(final String name) throws InvalidClassException {
        if (!predicate.test(name)) {
            invalidClassNameFound(name);
        }
    }

    
    protected void invalidClassNameFound(final String className) throws InvalidClassException {
        throw new InvalidClassException("Class name not accepted: " + className);
    }

    
    @SuppressWarnings("unchecked")
    public <T> T readObjectCast() throws ClassNotFoundException, IOException {
        return (T) super.readObject();
    }

    
    public ValidatingObjectInputStream reject(final Class<?>... classes) {
        predicate.reject(classes);
        return this;
    }

    
    public ValidatingObjectInputStream reject(final ClassNameMatcher matcher) {
        predicate.reject(matcher);
        return this;
    }

    
    public ValidatingObjectInputStream reject(final Pattern pattern) {
        predicate.reject(pattern);
        return this;
    }

    
    public ValidatingObjectInputStream reject(final String... patterns) {
        predicate.reject(patterns);
        return this;
    }

    
    @Override
    protected Class<?> resolveClass(final ObjectStreamClass osc) throws IOException, ClassNotFoundException {
        checkClassName(osc.getName());
        return super.resolveClass(osc);
    }

    
    @Override
    protected Class<?> resolveProxyClass(final String[] interfaces) throws IOException, ClassNotFoundException {
        for (final String interfaceName : interfaces) {
            checkClassName(interfaceName);
        }
        return super.resolveProxyClass(interfaces);
    }
}
