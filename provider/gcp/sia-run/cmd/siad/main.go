//
// Copyright The Athenz Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

package main

import (
	"flag"
	"fmt"
	"log"
	"os"
	"strings"

	"github.com/AthenZ/athenz/libs/go/sia/agent"
	sc "github.com/AthenZ/athenz/libs/go/sia/config"
	"github.com/AthenZ/athenz/libs/go/sia/gcp/meta"
	"github.com/AthenZ/athenz/libs/go/sia/options"
	"github.com/AthenZ/athenz/provider/gcp/sia-run"
)

// Following can be set by the build script using LDFLAGS

var Version string

const siaMainDir = "/var/lib/sia"

func main() {
	cmd := flag.String("cmd", "", "optional sub command to run")
	gcpMetaEndPoint := flag.String("meta", "http://169.254.169.254:80", "meta endpoint")
	ztsEndPoint := flag.String("zts", "", "Athenz Token Service (ZTS) endpoint")
	ztsServerName := flag.String("ztsservername", "", "ZTS server name for tls connections (optional)")
	ztsCACert := flag.String("ztscacert", "", "Athenz Token Service (ZTS) CA certificate file (optional)")
	dnsDomains := flag.String("dnsdomains", "", "DNS Domains associated with the provider")
	ztsPort := flag.Int("ztsport", 4443, "Athenz Token Service (ZTS) port number")
	pConf := flag.String("config", "/etc/sia/sia_config", "The config file to run against")
	providerPrefix := flag.String("providerprefix", "", "Provider name prefix e.g athenz.gcp")
	displayVersion := flag.Bool("version", false, "Display version information")

	flag.Parse()

	if *displayVersion {
		fmt.Println(Version)
		os.Exit(0)
	}

	log.SetFlags(log.LstdFlags)

	if *ztsEndPoint == "" {
		log.Fatalln("missing zts argument")
	}
	ztsUrl := fmt.Sprintf("https://%s:%d/zts/v1", *ztsEndPoint, *ztsPort)

	if *dnsDomains == "" {
		log.Fatalln("missing dnsdomains argument")
	}

	if *providerPrefix == "" {
		log.Fatalln("missing providerprefix argument")
	}

	log.Printf("SIA-RUN version: %s \n", Version)
	region := meta.GetRegion(*gcpMetaEndPoint)

	provider := sia.GCPRunProvider{
		Name: fmt.Sprintf("%s.%s", *providerPrefix, region),
	}

	config, err := sia.GetRunConfig(*pConf, *gcpMetaEndPoint, region, provider)
	if err != nil {
		log.Fatalf("Unable to formulate configuration objects, error: %v\n", err)
	}

	// backward compatibility sake, keeping the ConfigAccount struct
	configAccount := &sc.ConfigAccount{
		Name:      fmt.Sprintf("%s.%s", config.Domain, config.Service),
		User:      config.User,
		Group:     config.Group,
		Domain:    config.Domain,
		Account:   config.Account,
		Service:   config.Service,
		Zts:       config.Zts,
		Threshold: config.Threshold,
		Roles:     config.Roles,
	}

	opts, err := options.NewOptions(config, configAccount, nil, siaMainDir, Version, false, region)
	if err != nil {
		log.Fatalf("Unable to formulate options, error: %v\n", err)
	}

	instanceId, err := meta.GetInstanceId(*gcpMetaEndPoint)
	if err != nil {
		log.Fatalf("Unable to get instance id, error: %v\n", err)
	}

	opts.MetaEndPoint = *gcpMetaEndPoint
	opts.Ssh = false
	opts.ZTSCACertFile = *ztsCACert
	opts.ZTSServerName = *ztsServerName
	opts.ZTSCloudDomains = strings.Split(*dnsDomains, ",")
	opts.InstanceId = instanceId
	opts.Provider = provider
	opts.SpiffeNamespace = "default"

	agent.SetupAgent(opts, siaMainDir, "")
	agent.RunAgent(*cmd, ztsUrl, opts)
}
