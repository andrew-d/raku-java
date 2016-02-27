package io.dunham.raku;

import java.util.stream.Collectors;

import com.google.common.base.Joiner;
import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.hibernate.HibernateBundle;
import io.dropwizard.migrations.MigrationsBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dunham.raku.core.Document;
import io.dunham.raku.core.Tag;
import io.dunham.raku.db.DocumentDAO;
import io.dunham.raku.db.TagDAO;
import io.dunham.raku.resources.TagResource;
import io.dunham.raku.resources.TagsResource;
import io.dunham.raku.util.HibernateRunner;


public class RakuApplication extends Application<RakuConfiguration> {
    private static final Logger LOGGER = LoggerFactory.getLogger(RakuApplication.class);

    private final HibernateBundle<RakuConfiguration> hibernateBundle =
            new HibernateBundle<RakuConfiguration>(Document.class, Tag.class) {
                @Override
                public DataSourceFactory getDataSourceFactory(RakuConfiguration configuration) {
                    return configuration.getDataSourceFactory();
                }
            };

    @Override
    public String getName() {
        return "raku";
    }

    @Override
    public void initialize(Bootstrap<RakuConfiguration> bootstrap) {
        // Enable variable substitution with environment variables
        bootstrap.setConfigurationSourceProvider(
                new SubstitutingSourceProvider(
                        bootstrap.getConfigurationSourceProvider(),
                        new EnvironmentVariableSubstitutor(false)
                )
        );

        bootstrap.addBundle(new AssetsBundle());
        bootstrap.addBundle(new MigrationsBundle<RakuConfiguration>() {
            @Override
            public DataSourceFactory getDataSourceFactory(RakuConfiguration configuration) {
                return configuration.getDataSourceFactory();
            }
        });
        bootstrap.addBundle(hibernateBundle);
    }

    @Override
    public void run(RakuConfiguration configuration, Environment environment) {
        final SessionFactory sf = hibernateBundle.getSessionFactory();

        final DocumentDAO docDao = new DocumentDAO(sf);
        final TagDAO tagDao = new TagDAO(sf);

        // Register our resources
        environment.jersey().register(new TagResource(docDao, tagDao));
        environment.jersey().register(new TagsResource(docDao, tagDao));

        Tag tag1 = new Tag("foo");
        Tag tag2 = new Tag("bar");
        Tag tag3 = new Tag("asdf");

        HibernateRunner hr = new HibernateRunner(sf);
        try {
            hr.withHibernate(() -> {
                LOGGER.info("Creating tags...");
                tagDao.create(tag1);
                tagDao.create(tag2);
                tagDao.create(tag3);
                LOGGER.info("Tags created");
                return null;
            });

            hr.withHibernate(() -> {
                LOGGER.info("All tags:");
                for (Tag t : tagDao.findAll()) {
                    LOGGER.info(" - {}", t.getName());

                    Joiner j = Joiner.on(", ");
                    String documentNames = j.join(t
                        .getDocuments()
                        .stream()
                        .map(d -> d.getName())
                        .collect(Collectors.toList()));

                    LOGGER.info("   => docs: {}", documentNames);
                }
                return null;
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // This is the entry point that kicks things off.
    public static void main(String[] args) throws Exception {
        new RakuApplication().run(args);
    }
}
