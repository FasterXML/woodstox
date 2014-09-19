package wstxtest.cfg;

import javax.xml.stream.XMLInputFactory;

public interface InputTestConfig
{
    public boolean nextConfig(XMLInputFactory f);

    /**
     * Method that will reset iteration state to the initial, ie. state
     * before any iteration
     */
    public void firstConfig(XMLInputFactory f);

    /**
     * @return String that describes current settings this configuration
     *   Object has (has set when {@link #nextConfig} was called)
     */
    public String getDesc();
}
