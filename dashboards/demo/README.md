### Cluster Demo

This docker-compose script starts a small cluster with some workloads running and the dashboards
in one easy command!

To use:  

  1. From the project root make the agent (in Windows, you will need to do this under WSL):
     
     ````
     mvn -DskipTests package
     ````
     
  2. From this directory start the system, noting we need to parse the mcac-agent.jar version from the pom (in Windows, do this outside of WSL):
     
     ````
     export PROJECT_VERSION=$(grep '<revision>' ../../pom.xml | sed -E 's/(<\/?revision>|[[:space:]])//g')
     docker-compose up 
     ````
     
  3. Open your web browser to [http://localhost:3000](http://localhost:3000)
  
  If you want to change the jsonnet dashboards, make your changes under `mixin/dashboards/` then run:
  
  ````
  mixin/make-dashboards.sh
  ````
  
  Refresh the browser to see changes. 
