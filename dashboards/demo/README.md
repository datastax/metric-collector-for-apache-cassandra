### Cluster Demo

This docker-compose script starts a small cluster with some workloads running and the dashboards
in one easy command!

To use:  

  1. From the project root make the agent:
     
     ````mvn -DskipTests package````
     
  2. From this directory start the system:
     
     ````docker-compose up ````
     
  3. Open your web browser to [http://localhost:3000](http://localhost:3000)
  
  If you want to change the jsonnet dashboards, make your changes then run:
  
  ````../grafana/make-dashboards.sh````
  
  Refresh the browser to see changes. 