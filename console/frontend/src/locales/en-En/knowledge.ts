const translation = {
  // Upload page translations
  dataCleanFailed: 'Data cleaning failed',
  importWebsiteLinkSupport:
    'Supports reading static links, some links are not supported, please check the results',
  dataSettings: 'Data Settings',
  fileParsingEmbedding: 'File parsing, embedding...',
  knowledgeBaseCreated: 'Knowledge Base Created!',
  createNewKnowledge: 'Create Knowledge Base',
  documentCount: 'Documents',
  totalCharacters: 'Chars (K)',
  relatedAgents: 'Related Agents',
  // Modal translations
  confirmDeleteKnowledge: 'Confirm delete knowledge base?',
  deleteKnowledgeWarning:
    'Deleting the knowledge base is irreversible. Users will no longer be able to access your knowledge base, and all prompt configurations and logs will be permanently deleted.',
  createKnowledge: 'Create Knowledge Base',
  knowledgeName: 'Name:',
  knowledgeDescription: 'Description:',
  knowledgeVersion: 'Version:',
  xinghuoKnowledge: 'Spark Knowledge Base',
  xingpuDescription:
    'integrates multi-source heterogeneous knowledge with automated ingestion, delivers conversational Q&A retrieval, and enables answer tracing. It is tailored for robust enterprise applications.',
  xingchenKnowledge: 'Astra Knowledge Base',
  xingchenDescription:
    'integrates multi-source heterogeneous knowledge with automated ingestion, delivers conversational Q&A retrieval, and enables answer tracing. It is tailored for lightweight search scenarios.',
  confirm: 'Confirm',
  // Upload page translations
  fileUpload: 'Upload File',
  importData: 'Import Data',
  dataClean: 'Slice Preview',
  processingCompletion: 'Processing & Completion',
  nextStep: 'Next',
  previousStep: 'Previous Step',
  saveAndProcess: 'Save & Process',
  goToDocuments: 'Go to Documents',
  confirmLeave: 'Confirm leave?\nSome files failed to embed.',
  filesCount: 'and {{count}} more files',
  knowledgeCreated: 'Knowledge Base Created!',
  documentsUploaded:
    'Documents have been uploaded to the knowledge base. You can find them in the document list of the dataset.',
  fileParsing: 'File parsing, embedding...',
  embeddingCompleted: 'Embedding completed',
  embeddingFailed: 'Embedding failed',
  documentsEmbeddingFailed: 'Some documents failed to embed ({{count}})',
  retry: 'Retry',
  segmentSettings: 'Segment Settings',
  autoSegmentAndClean: 'Auto Segment & Clean',
  autoSegmentDescription:
    'Applies auto chunking and pre-processing rules. Recommended if you are unfamiliar with parameters.',
  custom: 'Custom',
  customDescription:
    'Set custom chunking rules and length, pre-processing rules and other parameters.',
  segmentIdentifier: 'Chunk Identifier',
  segmentLength: 'Chunk Length',
  segmentLengthSupport: 'Supported lengths:({{min}},{{max}})',
  documentsCleaningFailed: 'Some documents failed to clean ({{count}})',
  // File format descriptions
  xingchenFormatSupport:
    'Supports pdf, docx, doc, pptx, ppsx, txt, md, jpg, jpeg, png, bmp format documents. txt and md files limited to 10M, other files limited to 100M',
  sparkFormatSupport:
    'Supports pdf, doc, docx, txt, md, xlsx, xls, ppt, pptx, jpg, jpeg, png, bmp format documents. Single file limited to 20MB/1M characters, single image limited to 5M, images must contain text.',
  // Error messages
  uploadFileEmpty: 'Upload file cannot be empty!',
  fileSizeExceeded: 'File size cannot exceed {{size}}M!',
  uploadFileCountExceeded: 'Upload file count cannot exceed 10!',
  fileFormatIncorrect: 'File format is incorrect',
  // Import data translations
  chooseDataType: 'Data Type',
  importTextFile: 'Text File',
  importTextFileSupport:
    'Supports uploading files in TXT, PDF, MD, DOC and other formats',
  importWebsiteLink: ' Web URLs',
  dragAndDropFile: 'Drag & drop files here, or',
  selectFile: 'Select File',
  uploadWebsiteLink: 'Upload Website Link',
  websiteLinkSupport:
    'Currently only supports reading static links. Please check the results.',
  useNewlineToSeparate: 'Use newline to separate each link.',
  inputMultipleLinks: 'When entering multiple links, use newline, one per line',
  // Processing completion translations
  segmentationRules: 'Chunking Rule',
  automatic: 'Automatic',
  customized: 'Customized',
  paragraphLength: 'Chunking Length',
  characters: 'characters',
  averageParagraphLength: 'Average Chunking Length',
  paragraphCount: 'Chunking Count',
  paragraphs: 'paragraphs',
  // DataClean translations
  failedCount: 'Some documents failed to slice ({{count}})',
  segmentationSettings: 'Chunking Configuration',
  autoSegmentationAndCleaning: 'Auto Chunk & Clean',
  autoSegmentationAndCleaningDesc:
    'Applies auto chunking and pre-processing rules. Recommended if you are unfamiliar with parameters.',
  customDesc:
    'Set custom chunking rules and length, pre-processing rules and other parameters.',
  supportSegmentLength: 'Supported lengths:({{min}},{{max}})',
  preview: 'Preview',
  reset: 'Reset',
  indexingMethod: 'Indexing Method',
  highQuality: 'High-quality',
  highQualityDesc:
    "Leverages the system's default embedding API to deliver enhanced query accuracy.",
  segmentPreview: 'Chunk Preview',
  violationCount: 'Violation {{count}} groups',
  totalCount: 'Total {{count}} groups',
  downloadViolationDetails: 'Download Violation Details',
  violationReason: 'Violation reason: {{reason}}',
  slicing: 'Slicing, please wait...',
  // DataClean component translations
  pleaseEnter: 'Please enter',
  enterOrSelect: 'Enter or select',

  // KnowledgeHeader component translations
  document: 'Document',
  hitTest: 'Hit Test',
  settings: 'Settings',
  relatedApplications: 'Related Applications',

  // DocumentPage component translations
  documents: 'Documents',
  documentsDescription:
    'All files in the document knowledge base are displayed here. The entire knowledge base can be linked to applications or indexed through tools.',
  noDocumentsInKnowledge: 'No documents in knowledge base yet',
  addDocument: 'Add Document',
  addFolder: 'Add Folder',
  fileName: 'File Name',
  characterCount: 'Character Count',
  hitCount: 'Hit Count',
  uploadTime: 'Upload Time',
  status: 'Status',
  operations: 'Operations',
  enabled: 'Enabled',
  disabled: 'Disabled',
  items: 'items',

  // ModalComponents translations
  folder: 'Folder',
  folderName: 'Folder Name',
  confirmDeleteFile: 'Confirm delete file',
  confirmDeleteFolder: 'Confirm delete folder',
  confirmDeleteKnowledgeTag: 'Confirm delete knowledge base tag?',
  folderDeleteWarning:
    'Folder deletion cannot be undone. Documents within the folder will also be deleted.',
  fileDeleteWarning:
    'File deletion cannot be undone. Users will no longer be able to access the file',
  tagSettings: 'Tag Settings',
  addTags: 'Add Tags',
  addTagsDescription:
    'Separate multiple tags with commas. To delete knowledge base tags (yellow), please go to',
  knowledgeSettings: 'Knowledge Base Settings',

  // HitPage component translations
  hitTestDescription:
    'Test the hit effect of the knowledge base based on the given query text.',
  queryText: 'Query Text',
  query: 'Query',
  querying: 'ing',
  recentQueries: 'Recent Queries',
  queryTextHeader: 'Query Text',
  testTime: 'Test Time',
  hitParagraphs: 'Hit Paragraphs',
  hitKnowledgeParagraphsWillShowHere:
    'Hit knowledge paragraphs will be displayed here',

  // SettingPage component translations
  knowledgeSettingsDescription:
    'You can perform basic knowledge settings and model and indexing method settings for the knowledge base',
  knowledgeBaseName: 'Name',
  knowledgeBaseId: 'Knowledge Base ID: ',
  knowledgeBaseDescription: 'Knowledge Base Description',
  knowledgeBaseDescriptionDetail:
    'Please describe the content of the knowledge base in as much detail as possible so that AI can access knowledge faster',
  highQualityDescription:
    "Leverages the system's default embedding API to deliver enhanced query accuracy.",

  // FilePage component translations
  violationParagraphs: 'Violation {{count}} paragraphs',
  violationKnowledge: 'Violation Knowledge',
  manual: 'Manual',
  violation: 'Violation',
  technicalParameters: 'Technical Parameters',

  // FilePage ModalComponents translations
  uploadFileSizeExceeded: 'Upload file size cannot exceed 2M!',
  uploadImageFormatError: 'Please upload image files in JPG and PNG formats',
  knowledgeParagraph: 'Knowledge Paragraph',
  knowledgeParagraphRequired: 'Knowledge paragraph cannot be empty',
  addImage: 'Add Image',
  addImageDescription:
    'Upload formats include JPG, PNG, MP4 files. Please keep individual file size within 0MB-300MB. Only supports uploading 3 files',
  tags: 'Tags',
  addKnowledgeParagraph: 'Add Knowledge Paragraph',
  confirmDeleteParagraph: 'Confirm delete paragraph?',
  paragraphDeleteWarning:
    'Paragraph deletion cannot be undone. After deletion, the paragraph knowledge will not be retrievable and may affect subsequent conversation results.',
  save: 'Save',
  saveTip:
    'Clicking save does not affect data processing, and after processing, it can be referenced',
  progress: 'Progress',
  parseFail: 'Failed',
  parseSuccess: 'Success',
  confirmDisabled:
    'Are you sure to disable the workflow using the current knowledge base?',
  segmentPreviewWillBeAvailableAfterEmbedding:
    'Segmented preview will be available after embedding is complete',
};

export default translation;
