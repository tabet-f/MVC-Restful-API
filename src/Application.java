package hello;


import java.io.IOException;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.config.annotation.configurers.ClientDetailsServiceConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configuration.AuthorizationServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableAuthorizationServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerEndpointsConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configurers.ResourceServerSecurityConfigurer;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;


@SpringBootApplication
public class Application {
	

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
        
        //Call Open CMD
        //openCommandLine();
       
    }

    @Bean
    public CommandLineRunner commandLineRunner(ApplicationContext ctx) {
        return args -> {

            System.out.println("Let's inspect the beans provided by Spring Boot:");

            String[] beanNames = ctx.getBeanDefinitionNames();
            Arrays.sort(beanNames);
            for (String beanName : beanNames) {
                System.out.println(beanName);
            }

        };
    }
    

//    @Bean
//    public static ShallowEtagHeaderFilter shallowEtagHeaderFilter() {
//      
//        System.out.println(new ShallowEtagHeaderFilter());
//       
//        return new ShallowEtagHeaderFilter();
//    }
    
   
  
	@Configuration
	@EnableResourceServer
	protected static class ResourceServer extends ResourceServerConfigurerAdapter {

		@Override
		public void configure(HttpSecurity http) throws Exception {
			// @formatter:off
			http
			   
				//apply OAuth protection to only 1 resource
				.requestMatcher(new OrRequestMatcher(
					new AntPathRequestMatcher("/schema"),
					new AntPathRequestMatcher("/user"),
					new AntPathRequestMatcher("/flushAll"),
					new AntPathRequestMatcher("/flushAllKeepSchema"),
					new AntPathRequestMatcher("/search/{user_id}"),
					new AntPathRequestMatcher("/user/{user_id}")
				))
				
				.authorizeRequests()
				//.anyRequest().permitAll(); //DISABLE AUTHORIZATION
				.anyRequest().access("#oauth2.hasScope('read')"); //ENABLE OAUTH 2 AUTHORIZATION
			// @formatter:on
		}

		@Override
		public void configure(ResourceServerSecurityConfigurer resources)
				throws Exception {
			resources.resourceId("sparklr");
		}

	}

	@Configuration
	@EnableAuthorizationServer
	protected static class OAuth2Config extends AuthorizationServerConfigurerAdapter {

		@Autowired
		private AuthenticationManager authenticationManager;

		@Override
		public void configure(AuthorizationServerEndpointsConfigurer endpoints)
				throws Exception {
			endpoints.authenticationManager(authenticationManager);
		}

		@Override
		public void configure(ClientDetailsServiceConfigurer clients) throws Exception {
			// @formatter:off
			clients.inMemory().withClient("my-trusted-client")
					.authorizedGrantTypes("password", "authorization_code",
							"refresh_token", "implicit")
					.authorities("ROLE_CLIENT", "ROLE_TRUSTED_CLIENT")
					.scopes("read", "write", "trust").resourceIds("sparklr")
					.accessTokenValiditySeconds(600).and()
					.withClient("my-client-with-registered-redirect")
					.authorizedGrantTypes("authorization_code").authorities("ROLE_CLIENT")
					.scopes("read", "trust").resourceIds("sparklr")
					.redirectUris("http://anywhere?key=value").and()
					.withClient("my-client-with-secret")
					.authorizedGrantTypes("client_credentials", "password")
					.authorities("ROLE_CLIENT").scopes("read").resourceIds("sparklr")
					.secret("secret");
			// @formatter:on
		}

	}

	//Open Command Line CMD
	public static void openCommandLine(){
    
		try {
  	
			String path = "C:\\Users\\donate";
			Runtime.getRuntime().exec(new String[] { "cmd.exe", "/C", "\"start; cd "+path+"\"" });
			System.out.println("Command Line Ready.");
  
		} catch (IOException ex) {
      ex.printStackTrace();
	  }
	}


}
