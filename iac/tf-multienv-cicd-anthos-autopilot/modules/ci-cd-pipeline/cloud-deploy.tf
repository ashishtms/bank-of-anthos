# Copyright 2022 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# create CloudDeploy targets
resource "google_clouddeploy_target" "targets" {
  # one CloudDeploy target per target defined in vars
  for_each = toset(var.targets)

  project  = var.project_id
  name     = "${each.value}-${local.service_name}"
  location = var.region

  anthos_cluster {
    membership = var.cluster_memberships[each.key].id
  }

  execution_configs {
    artifact_storage = "gs://${google_storage_bucket.delivery_artifacts.name}"
    service_account  = google_service_account.cloud_deploy.email
    usages = [
      "RENDER",
      "DEPLOY",
      "VERIFY"
    ]
  }
}

# create delivery pipeline for service including all targets
resource "google_clouddeploy_delivery_pipeline" "delivery-pipeline" {
  project  = var.project_id
  location = var.region
  name     = var.service
  serial_pipeline {
    dynamic "stages" {
      for_each = { for idx, target in var.targets : idx => target }
      content {
        profiles  = [stages.value]
        target_id = google_clouddeploy_target.targets[stages.value].name
        strategy {
          standard {
            verify = true
          }
        }
      }
    }
  }
  provider = google-beta
}
