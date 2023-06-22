# sunbird-infra-provision

## Infra provision on azure
### Pre-requisites:
* Install azure cli(az) tool on local machine and login to azure account with "az login" command.
* Create a azure service account to be used for the infra provision.
* Replace the default values in infra-provision/azure/variables.tf .

>*[ Go to the repository path: knowledge-platform/knowlg-automation/terraform ]*
### Create Infra on azure:
```shell   
sh create    
```
> *provide option as "azure"*
### Destroy Infra on azure: 
***Note:** [append --auto-approve to continue without confirmation.]*
```shell    
sh destroy    
```
>*provide option as "azure"*    


## Infra provision on local
### Pre-requisites:
* Terraform to be installed .
* For local provision, kind provider is used to provision the cluster.  

>*[ Go to the repository path: knowledge-platform/knowlg-automation/terraform ]*
### Create Infra on local:
```shell   
sh create    
```
> *provide option as "local"*
### Destroy Infra on local: 
***Note:** [append --auto-approve to continue without confirmation.]*
```shell    
sh destroy    
```
>*provide option as "local"*      
