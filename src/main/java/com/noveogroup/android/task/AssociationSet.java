package com.noveogroup.android.task;

import java.util.*;

/**
 * An association set.
 *
 * @param <V> a type of the values.
 */
public class AssociationSet<V> {

    private final Map<V, Set<String>> values = new HashMap<V, Set<String>>();
    private final Set<Set<String>> interruptedTags = new HashSet<Set<String>>();

    /**
     * Adds a value to the set and associates it with the tags.
     *
     * @param value the value.
     * @param tags  the tags
     */
    public void add(V value, Collection<String> tags) {
        values.put(value, new HashSet<String>(tags));
    }

    /**
     * Removes a value from the set.
     *
     * @param value the value.
     */
    public void remove(V value) {
        values.remove(value);
    }

    /**
     * Returns a set of associated values from the set.
     *
     * @param tags the tags to find values associated with.
     * @return the set of values.
     */
    // todo optimize it
    public Set<V> getAssociated(Collection<String> tags) {
        Set<V> set = new HashSet<V>();
        for (Map.Entry<V, Set<String>> entry : values.entrySet()) {
            if (entry.getValue().containsAll(tags)) {
                set.add(entry.getKey());
            }
        }
        return Collections.unmodifiableSet(set);
    }

    // todo optimize it
    public boolean isInterrupted(Collection<String> tags) {
        boolean isInterrupted = false;
        for (Iterator<Set<String>> iterator = interruptedTags.iterator(); iterator.hasNext(); ) {
            Set<String> set = iterator.next();
            if (tags.containsAll(set)) {
                if (getAssociated(set).isEmpty()) {
                    iterator.remove();
                } else {
                    isInterrupted = true;
                }
            }
        }
        return isInterrupted;
    }

    public void interrupt(Collection<String> tags) {
        interruptedTags.add(new HashSet<String>(tags));
    }

}
