package de.caluga.morphium;

/**
 * User: Stephan Bösebeck
 * Date: 19.06.12
 * Time: 11:47
 * <p/>
 * NameProvider define the name for a given Collection
 */
public interface NameProvider {
    @SuppressWarnings("UnusedParameters")
    String getCollectionName(Class<?> type, ObjectMapper om, boolean translateCamelCase, boolean useFQN, String specifiedName, Morphium morphium);
}
