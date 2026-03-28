const fs = require('fs');
const os = require('os');
const path = require('path');

const EAS_CLI_ROOT = 'C:/Program Files/nodejs/node_modules/eas-cli/build';
const PROJECT_FULL_NAME = '@shivarya3/location-speed-tracker';
const ANDROID_APPLICATION_IDENTIFIER = 'com.shivarya.locationspeedtracker';
const DEFAULT_BUILD_CREDENTIALS_NAME = 'production';

function parseProperties(content) {
  const result = {};

  for (const rawLine of content.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith('#') || line.startsWith('!')) {
      continue;
    }

    const separatorIndex = line.search(/[:=]/);
    if (separatorIndex === -1) {
      continue;
    }

    const key = line.slice(0, separatorIndex).trim();
    const value = line.slice(separatorIndex + 1).trim();
    if (key) {
      result[key] = value;
    }
  }

  return result;
}

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

function resolveKeystoreFile(androidDir, storeFileValue) {
  const candidatePaths = [
    path.isAbsolute(storeFileValue)
      ? storeFileValue
      : path.resolve(androidDir, '..', storeFileValue),
    path.isAbsolute(storeFileValue)
      ? storeFileValue
      : path.resolve(androidDir, storeFileValue),
  ];

  for (const candidate of candidatePaths) {
    if (fs.existsSync(candidate)) {
      return candidate;
    }
  }

  throw new Error(`Could not find keystore file referenced by storeFile=${storeFileValue}`);
}

function loadSigningMaterial() {
  const projectRoot = process.cwd();
  const androidDir = path.join(projectRoot, 'android');
  if (!fs.existsSync(androidDir)) {
    throw new Error('android directory not found. Run this script from project root.');
  }

  const propertyCandidates = [
    path.join(androidDir, 'keystore.play.properties'),
    path.join(androidDir, 'keystore.properties'),
  ];

  const propertiesPath = propertyCandidates.find((filePath) => fs.existsSync(filePath));
  if (!propertiesPath) {
    throw new Error('No keystore properties file found (expected android/keystore.play.properties or android/keystore.properties).');
  }

  const properties = parseProperties(fs.readFileSync(propertiesPath, 'utf8'));

  const storeFile = properties.storeFile;
  const storePassword = properties.storePassword;
  const keyAlias = properties.keyAlias;
  const keyPassword = properties.keyPassword || storePassword;

  if (!storeFile || !storePassword || !keyAlias || !keyPassword) {
    throw new Error(`Signing properties are incomplete in ${path.basename(propertiesPath)}.`);
  }

  const keystorePath = resolveKeystoreFile(androidDir, storeFile);
  const keystoreBase64 = fs.readFileSync(keystorePath).toString('base64');

  return {
    keystoreBase64,
    keystorePassword: storePassword,
    keyAlias,
    keyPassword,
    keystorePath,
    propertiesPath,
  };
}

async function uploadKeystoreToEas(signingMaterial) {
  const { createGraphqlClient } = require(`${EAS_CLI_ROOT}/commandUtils/context/contextUtils/createGraphqlClient`);
  const { AppQuery } = require(`${EAS_CLI_ROOT}/graphql/queries/AppQuery`);
  const {
    createAndroidAppBuildCredentialsAsync,
    createKeystoreAsync,
    getDefaultAndroidAppBuildCredentialsAsync,
    updateAndroidAppBuildCredentialsAsync,
  } = require(`${EAS_CLI_ROOT}/credentials/android/api/GraphqlClient`);

  const [accountName, projectName] = PROJECT_FULL_NAME.replace(/^@/, '').split('/');
  if (!accountName || !projectName) {
    throw new Error(`Invalid PROJECT_FULL_NAME format: ${PROJECT_FULL_NAME}`);
  }

  const sessionSecretCandidates = resolveSessionSecretCandidates();
  let lastError;

  for (const sessionSecret of sessionSecretCandidates) {
    try {
      const graphqlClient = createGraphqlClient({
        accessToken: null,
        sessionSecret,
      });

      const app = await AppQuery.byFullNameAsync(graphqlClient, PROJECT_FULL_NAME);
      if (!app?.ownerAccount) {
        throw new Error(`Could not load app ${PROJECT_FULL_NAME} from EAS.`);
      }

      const account = app.ownerAccount;
      const appLookupParams = {
        account: { id: account.id, name: account.name },
        projectName,
        androidApplicationIdentifier: ANDROID_APPLICATION_IDENTIFIER,
      };

      const createdKeystore = await createKeystoreAsync(
        graphqlClient,
        account,
        {
          keystore: signingMaterial.keystoreBase64,
          keystorePassword: signingMaterial.keystorePassword,
          keyAlias: signingMaterial.keyAlias,
          keyPassword: signingMaterial.keyPassword,
        }
      );

      const defaultBuildCredentials = await getDefaultAndroidAppBuildCredentialsAsync(
        graphqlClient,
        appLookupParams
      );

      if (defaultBuildCredentials) {
        await updateAndroidAppBuildCredentialsAsync(graphqlClient, defaultBuildCredentials, {
          androidKeystoreId: createdKeystore.id,
        });
      } else {
        await createAndroidAppBuildCredentialsAsync(graphqlClient, appLookupParams, {
          name: DEFAULT_BUILD_CREDENTIALS_NAME,
          isDefault: true,
          androidKeystoreId: createdKeystore.id,
        });
      }

      return {
        accountName,
        projectName,
        androidApplicationIdentifier: ANDROID_APPLICATION_IDENTIFIER,
        keystoreId: createdKeystore.id,
      };
    } catch (error) {
      lastError = error;
    }
  }

  throw lastError;
}

async function main() {
  const signingMaterial = loadSigningMaterial();

  const result = await uploadKeystoreToEas(signingMaterial);

  console.log(`Uploaded Android keystore to EAS for ${result.accountName}/${result.projectName}.`);
  console.log(`Application ID: ${result.androidApplicationIdentifier}`);
  console.log(`Created keystore ID: ${result.keystoreId}`);
  console.log(`Source properties: ${path.basename(signingMaterial.propertiesPath)}`);
  console.log(`Source keystore: ${path.basename(signingMaterial.keystorePath)}`);
}

main().catch((error) => {
  console.error(error?.message || error);
  process.exit(1);
});
