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

async function queryCredentials(legacyOnly) {
  const { createGraphqlClient } = require(`${EAS_CLI_ROOT}/commandUtils/context/contextUtils/createGraphqlClient`);
  const {
    AndroidAppCredentialsQuery,
  } = require(`${EAS_CLI_ROOT}/credentials/android/api/graphql/queries/AndroidAppCredentialsQuery`);

  const sessionSecretCandidates = resolveSessionSecretCandidates();
  let lastError;

  for (const sessionSecret of sessionSecretCandidates) {
    try {
      const graphqlClient = createGraphqlClient({ accessToken: null, sessionSecret });
      const result = await AndroidAppCredentialsQuery.withCommonFieldsByApplicationIdentifierAsync(
        graphqlClient,
        PROJECT_FULL_NAME,
        {
          androidApplicationIdentifier: ANDROID_APPLICATION_IDENTIFIER,
          legacyOnly,
        }
      );
      return result;
    } catch (error) {
      lastError = error;
    }
  }

  throw lastError;
}

function printCredentialsBlock(label, appCredentials) {
  console.log(`\n=== ${label} ===`);
  if (!appCredentials) {
    console.log('No credentials found.');
    return;
  }

  const list = appCredentials.androidAppBuildCredentialsList || [];
  if (!list.length) {
    console.log('No build credential entries found.');
    return;
  }

  for (const item of list) {
    const k = item.androidKeystore;
    console.log(`name=${item.name}`);
    console.log(`isDefault=${item.isDefault}`);
    console.log(`isLegacy=${item.isLegacy}`);
    console.log(`keystoreId=${k?.id || 'N/A'}`);
    console.log(`sha1=${k?.sha1CertificateFingerprint || 'N/A'}`);
    console.log(`sha256=${k?.sha256CertificateFingerprint || 'N/A'}`);
    console.log(`md5=${k?.md5CertificateFingerprint || 'N/A'}`);
    console.log('---');
  }
}

async function main() {
  const modern = await queryCredentials(false);
  const legacy = await queryCredentials(true);

  printCredentialsBlock('EAS Modern Credentials', modern);
  printCredentialsBlock('EAS Legacy Credentials', legacy);
}

main().catch((error) => {
  console.error(error?.message || error);
  process.exit(1);
});
