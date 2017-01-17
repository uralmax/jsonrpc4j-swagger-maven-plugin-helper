# Jsonrpc4j Swagger Maven Plugin Helper

This configs enables your Swagger-annotated project to generate **Swagger specs** by swagger-maven-plugin(https://github.com/kongchen/swagger-maven-plugin) for jsonrpc4j

# Features

* Supports [Swagger Spec 2.0](https://github.com/swagger-api/swagger-spec/blob/master/versions/2.0.md)
* Supports [jsonrpc4j](https://github.com/briandilley/jsonrpc4j)
* Use [Handlebars](http://handlebarsjs.com/) as template to customize the static document.
* Use [swagger-maven-plugin](https://github.com/kongchen/swagger-maven-plugin/) for this config

# Usage
Import the swagger-maven-plugin plugin in your project by adding following configuration in your `plugins` block:

```xml
<build>
	<plugins>
		<plugin>
			<groupId>com.github.kongchen</groupId>
			<artifactId>swagger-maven-plugin</artifactId>
			<version>${swagger-maven-plugin-version}</version>
			<configuration>
				<apiSources>
					<apiSource>
						...
					</apiSource>
				</apiSources>
			</configuration>
		</plugin>
	</plugins>
</build>
```

# Configuration for swagger-maven-plugin `configuration`
https://github.com/kongchen/swagger-maven-plugin
# Add config helper
```xml
            <dependency>
                <groupId>com.github.uralmax</groupId>
                <artifactId>jsonrpc4j-swagger-maven-plugin-helper</artifactId>
                <version>${jsonrpc4j-swagger-maven-plugin-helper.version}</version>
                <scope>compile</scope>
            </dependency>
```

## A Sample Configuration whith this config

```xml
<project>
...
<build>
<plugins>
<plugin>
    <groupId>com.github.kongchen</groupId>
    <artifactId>swagger-maven-plugin</artifactId>
    <version>3.1.4</version>
    <configuration>
        <apiSources>
            <apiSource>
	            <springmvc>true</springmvc>
                <locations>
                    <location>com.githud.uralmax</location>
                </locations>
                <schemes>
                    <scheme>http</scheme>
                    <scheme>https</scheme>
                </schemes>
                <host>www.example.com:8080</host>
                <basePath>/api</basePath>
                <info>
                    <title>Swagger Maven Plugin Sample</title>
                    <version>v1</version>
                    <description>
                        This is a sample.
                    </description>
                </info>
                <templatePath>classpath:/templates/strapdown.html.hbs</templatePath>
                <outputPath>${basedir}/src/main/webapp/WEB-INF/swagger-api/docs.html</outputPath>
                <swaggerApiReader>com.github.uralmax.JsonRpcSwaggerApiReader</swaggerApiReader>
                <swaggerDirectory>${basedir}/src/main/webapp/WEB-INF/swagger-api</swaggerDirectory>
            </apiSource>
        </apiSources>
    </configuration>
    <executions>
        <execution>
            <phase>compile</phase>
            <goals>
                <goal>generate</goal>
            </goals>
        </execution>
    </executions>
</plugin>
...
</plugins>
</build>
</project>
```

## Show results
To show results add config for resource     <mvc:resources mapping="/swagger-api/**" location="classpath:/WEB-INF/swagger-api/"/>
