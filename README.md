# ckan-maven-plugin
A maven plugin to upload artifacts to CKAN

## Usage

* **Step 1:** Register the server password in your `$HOME/.m2/settings.xml` file.
It is strongly advised to [encrypt your passwords](https://maven.apache.org/guides/mini/guide-encryption.html) in this file.

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
  http://maven.apache.org/xsd/settings-1.0.0.xsd">

  <servers>
    <server>
      <id>the.ckan.serverId</id>
      <username>CKAN_USER_NAME</username>
      <password>CKAN_API_KEY</password>
    </server>
  </servers>
</settings>
```

* **Step2:** Add the ckan-maven-plugin to your project to upload data.
```xml
<plugin>
    <groupId>org.aksw.maven.plugins</groupId>
    <artifactId>ckan-maven-plugin</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <executions>
      <execution>
        <id>ckan-upload</id>
        <phase>deploy</phase>
        <goals>
          <goal>upload</goal>
        </goals>            
        <configuration>
          <ckanUrl>https://the.ckan.server/</ckanUrl>
          <serverId>the.ckan.serverId</serverId>
          <datasetId>Your Dataset</datasetId>
          <resourceId>${project.artifactId}</resourceId>
          <fileName>${project.build.directory}/YOUR_FILE.XYZ</fileName>
          <organizationId>TheOrganization</organizationId>
          <author>TheAuthor</author>
        </configuration>
      </execution>
    </executions>            
</plugin>
```

## Notes

* The first license of a `<licenses>` section will be set as the CKAN dataset license. If there is more than one a warning will be logged.
* The `<description>` of the pom becomes the description of the CKAN dataset.

## Known Issues

If there is only CKAN deployment but no standard maven deployment using a `<distributionManagement>` section then the `maven-deploy-plugin` will raise an error.
Currently this can be mitigated either using the command line property:

`mvn -D maven.deploy.skip deploy`

Or it is possible to disable the `default-deploy` execution of the `maven-deploy-plugin` with the following plugin declaration:

```xml
<plugin>
    <groupId>org.aksw.maven.plugins</groupId>
    <artifactId>ckan-deploy-plugin</artifactId>
    <version>3.1.1</version>
    <executions>
      <execution>
        <id>default-deploy</id>
        <configuration>
          <skip>true</skip>
        </configuration>
      </execution>
    </executions>
</plugin>
```

