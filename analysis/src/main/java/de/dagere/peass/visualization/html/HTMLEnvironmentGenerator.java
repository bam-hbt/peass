package de.dagere.peass.visualization.html;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import de.dagere.peass.visualization.VisualizeRCAStarter;

public class HTMLEnvironmentGenerator {

   final BufferedWriter fileWriter;

   public HTMLEnvironmentGenerator(final BufferedWriter fileWriter) {
      this.fileWriter = fileWriter;
   }

   public void writeHTML(final String name) throws IOException {
      final InputStream htmlStream = VisualizeRCAStarter.class.getClassLoader().getResourceAsStream(name);
      try (final BufferedReader reader = new BufferedReader(new InputStreamReader(htmlStream))) {
         String line;
         while ((line = reader.readLine()) != null) {
            fileWriter.write(line + "\n");
         }
      }
   }
}
