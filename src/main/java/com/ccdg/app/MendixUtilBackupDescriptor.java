package main.java.com.ccdg.app;

public class MendixUtilBackupDescriptor 
{
	    private String SnapshotID;

	    private String ExpiresOn;

	    private String State;

	    private String ModelVersion;

	    private String CreatedOn;

	    private String Comment;

	    public String getSnapshotID ()
	    {
	        return SnapshotID;
	    }

	    public void setSnapshotID (String SnapshotID)
	    {
	        this.SnapshotID = SnapshotID;
	    }

	    public String getExpiresOn ()
	    {
	        return ExpiresOn;
	    }

	    public void setExpiresOn (String ExpiresOn)
	    {
	        this.ExpiresOn = ExpiresOn;
	    }

	    public String getState ()
	    {
	        return State;
	    }

	    public void setState (String State)
	    {
	        this.State = State;
	    }

	    public String getModelVersion ()
	    {
	        return ModelVersion;
	    }

	    public void setModelVersion (String ModelVersion)
	    {
	        this.ModelVersion = ModelVersion;
	    }

	    public String getCreatedOn ()
	    {
	        return CreatedOn;
	    }

	    public void setCreatedOn (String CreatedOn)
	    {
	        this.CreatedOn = CreatedOn;
	    }

	    public String getComment ()
	    {
	        return Comment;
	    }

	    public void setComment (String Comment)
	    {
	        this.Comment = Comment;
	    }

	    @Override
	    public String toString()
	    {
	        return "ClassPojo [SnapshotID = "+SnapshotID+", ExpiresOn = "+ExpiresOn+", State = "+State+", ModelVersion = "+ModelVersion+", CreatedOn = "+CreatedOn+", Comment = "+Comment+"]";
	    }
	}