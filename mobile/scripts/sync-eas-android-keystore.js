const fs = require('fs');
const os = require('os');
const path = require('path');

const EAS_CLI_ROOT = 'C:/Program Files/nodejs/node_modules/eas-cli/build';
const PROJECT_FULL_NAME = '@shivarya3/location-speed-tracker';
const ANDROID_APPLICATION_IDENTIFIER = 'com.shivarya.locationspeedtracker';

function resolveSessionSecretCandidates() {
  const statePath = path.join(os.homedir(), '.expo', 'state.json');
  if (!fs.existsSync(statePath)) {
    throw new Error('Expo state file not found. Please run eas login first.');
  }

  const state = JSON.parse(fs.readFileSync(statePath, 'utf8'));
  const rawSession = state?.auth?.sessionSecret;
  if (!rawSession) {
    throw new Error('No Expo session secret found. Please run eas login first.');
  }

  const candidates = [rawSession];

  // Some CLI versions serialize auth info as a JSON string object.
  // Keep both forms to maximize compatibility.
  try {
    const parsed = JSON.parse(rawSession);
    if (parsed?.id) {
      candidates.unshift(parsed.id);
    }
  } catch {
    // No-op.
  }

  return [...new Set(candidates.filter(Boolean))];
}

async function fetchDefaultAndroidKeystore() {
  const { createGraphqlClient } = require(`${EAS_CLI_ROOT}/commandUtils/context/contextUtils/createGraphqlClient`);
  const {
    AndroidAppCredentialsQuery,
  } = require(`${EAS_CLI_ROOT}/credentials/android/api/graphql/queries/AndroidAppCredentialsQuery`);

  const sessionSecretCandidates = resolveSessionSecretCandidates();
  let lastError;

  for (const sessionSecret of sessionSecretCandidates) {
    try {
      const graphqlClient = createGraphqlClient({
        accessToken: null,
        sessionSecret,
      });

      const appCredentials = await AndroidAppCredentialsQuery.withCommonFieldsByApplicationIdentifierAsync(
        graphqlClient,
        PROJECT_FULL_NAME,
        {
          androidApplicationIdentifier: ANDROID_APPLICATION_IDENTIFIER,
          legacyOnly: false,
        }
      );

      if (!appCredentials?.androidAppBuildCredentialsList?.length) {
        throw new Error('No Android build credentials found on EAS for this app.');
      }

      const buildCredentials =
        appCredentials.androidAppBuildCredentialsList.find((item) => item.isDefault) ??
        appCredentials.androidAppBuildCredentialsList[0];

      if (!buildCredentials?.androidKeystore?.keystore) {
        throw new Error('EAS returned build credentials without a keystore payload.');
      }

      return buildCredentials.androidKeystore;
    } catch (error) {
      lastError = error;
    }
  }

  throw lastError;
}

function writeLocalSigningFiles(keystore) {
  const androidDir = path.join(process.cwd(), 'android');
  if (!fs.existsSync(androidDir)) {
    throw new Error('android directory not found. Run this script from project root.');
  }

  const keystoreFilename = 'location-tracker-release.keystore';
  const keystorePath = path.join(androidDir, keystoreFilename);
  fs.writeFileSync(keystorePath, keystore.keystore, 'base64');

  const keyPassword = keystore.keyPassword || keystore.keystorePassword;
  const keystorePropertiesPath = path.join(androidDir, 'keystore.properties');
  const keystorePropertiesContent = [
    `storeFile=${keystoreFilename}`,
    `storePassword=${keystore.keystorePassword}`,
    `keyAlias=${keystore.keyAlias}`,
    `keyPassword=${keyPassword}`,
  ].join('\n');

  fs.writeFileSync(keystorePropertiesPath, `${keystorePropertiesContent}\n`, 'utf8');
}

async function main() {
  const keystore = await fetchDefaultAndroidKeystore();
  writeLocalSigningFiles(keystore);
  console.log('Synced EAS Android keystore into android/keystore.properties and android/location-tracker-release.keystore');
}

main().catch((error) => {
  console.error(error?.message || error);
  process.exit(1);
});
