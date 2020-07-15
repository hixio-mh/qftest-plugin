package org.jenkinsci.plugins.qftest;

import hudson.util.ArgumentListBuilder;

import java.util.*;
import java.util.stream.Stream;

public class ExtendedArgumentListBuilder extends ArgumentListBuilder {

    boolean skipValue = false;
    private Map<String, String> overwrites = new HashMap();
    private Map<String, String> defaults = new HashMap();
    private Map<String, String> drops = new HashMap();

    private List<String> alteredArgs = new ArrayList<String>();

    public enum PresetType {
        ENFORCE,
        OVERWRITE,
        DROP,
        DEFAULT
    }

    public ExtendedArgumentListBuilder presetArg(PresetType type, String key, String value) {
        switch(type) {
            case DROP:      //drop this key and if value != null drop also the value
                drops.put(key, value); break;
            case DEFAULT:   //set a default value
                super.add(key, false);
                if (value != null) {
                    super.add(value, false);
                }
                defaults.put(key, value); break;
            case OVERWRITE: //overwrite the value belonging the key
                overwrites.put(key, value); break;
            case ENFORCE:   //always set exactly these values
                add(key, false);
                if (value != null) {
                    add(value, false);
                }
                drops.put(key, value); break;
        }
        return this;
    }

    public ExtendedArgumentListBuilder presetArg(PresetType type, String key) throws IllegalArgumentException {
        if (type == PresetType.DEFAULT || type == PresetType.OVERWRITE) {
            throw new IllegalArgumentException("This preset type requires an value argument.");
        }

        return presetArg(type, key, null);
    }

    public ExtendedArgumentListBuilder add(String arg, boolean mask) {

        //will this argument be altered by preset definitions?
        if (skipValue || drops.containsKey(arg) || overwrites.containsKey(arg)) {
            alteredArgs.add(arg);

            if (skipValue) {
                skipValue = false;

            } else if (drops.containsKey(arg)) {
                //drop key (and value?)
                if (drops.get(arg) != null) {
                    skipValue = true;
                }
            } else if (overwrites.containsKey(arg)) {
                //overwrite value
                super.add(arg, mask);
                super.add(overwrites.get(arg), mask);
                skipValue = true;
            }
        } else if (defaults.containsKey(arg)) {
            //already added..remove old values and take these
            final List addedArgs = this.toList();
            final int idx = addedArgs.indexOf(arg);

            addedArgs.remove(idx);
            super.add(arg, mask);

           if (defaults.get(arg) != null) {
               addedArgs.remove(idx);
           }

        } else {
            super.add(arg, mask);
        }
        return this;
    }

    public List getAlteredArgs() {
        return Collections.unmodifiableList(alteredArgs);
    }

}
