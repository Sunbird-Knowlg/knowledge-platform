# sunbird-infra-provision

Infra provision on Azure

Pre-requisites:
Install azure cli(az) tool on local machine and login to azure account with "az login" command.
Create a azure service account to be used for the infra provision.
Copy the ingestion spec to any folder and share the path in variables
Replace the default values in infra-provision/azure/vars.tf .

Steps:
Run create shell script and provide option input as "azure" .


Destroy infra on Azure
Steps:
Run destroy shell script with option as "azure" and append --auto-approve to continue without confirmation.


Infra provision on local

Pre-requisites:
For local provision, kind provider is used to provision the cluster.
Steps:
Run shell script and provide option as "local"

Destroy infra on local
Steps:
Run destroy shell script and provide option as "local" append --auto-approve to continue without confirmation.
